package scorex.perma.consensus

import scorex.block.{Block, TransactionalData}
import scorex.consensus.{ConsensusModule, ConsensusSettings, StoredBlockchain}
import scorex.crypto.hash.FastCryptographicHash._
import scorex.crypto.hash.{Blake2b256, CryptographicHash, FastCryptographicHash}
import scorex.perma.settings.PermaConstants
import scorex.perma.settings.PermaConstants._
import scorex.settings.Settings
import scorex.settings.SizedConstants._
import scorex.storage.Storage
import scorex.transaction._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.proof.Signature25519
import scorex.transaction.state.PrivateKey25519Holder
import scorex.transaction.wallet.Wallet
import scorex.utils.{NTP, Random, ScorexLogging}
import shapeless.Sized

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Data and functions related to a Permacoin consensus protocol
 */
class PermaConsensusModule[TX <: Transaction[PublicKey25519Proposition, TX], TData <: TransactionalData[TX]]
(rootHash: Sized[Array[Byte], Nat32], val settings: Settings with ConsensusSettings,
 override val transactionalModule: TransactionalModule[PublicKey25519Proposition, TX, TData])
(implicit val authDataStorage: Storage[Long, PermaAuthData])
  extends ConsensusModule[PublicKey25519Proposition, TX, TData, PermaConsensusBlockData]
  with StoredBlockchain[PublicKey25519Proposition, PermaConsensusBlockData, TX, TData]
  with ScorexLogging {

  val BlockReward = 1000000
  val InitialTarget = PermaConstants.initialTarget
  val initialTargetPow: BigInt = log2(InitialTarget)
  val TargetRecalculation = PermaConstants.targetRecalculation
  val AvgDelay = PermaConstants.averageDelay
  implicit val Hash = FastCryptographicHash

  val SSize = 32
  val GenesisCreator = PublicKey25519Proposition(Sized.wrap(Array.fill(32)(0: Byte)))
  val Version: Byte = 2

  type PubKey = PublicKey25519Proposition
  type PermaBlock = Block[PubKey, TData, PermaConsensusBlockData]
  type TM = TransactionalModule[PubKey, TX, TData]

  private def miningReward(block: PermaBlock): Long = BlockReward

  private def blockGenerator(block: PermaBlock): PubKey = block.consensusData.producer

  override val BlockIdLength: Int = 64

  override def producers(block: PermaBlock): Seq[PubKey] = Seq(blockGenerator(block))

  override def parentId(block: PermaBlock): BlockId = block.consensusData.parentId

  override def id(block: PermaBlock): BlockId = block.consensusData.blockId


  override val genesisData: PermaConsensusBlockData = {
    val t = Ticket(GenesisCreator.publicKey, Array.fill(SSize)(0: Byte), IndexedSeq())
    PermaConsensusBlockData(Array.fill(BlockIdLength)(0: Byte), Array.fill(BlockIdLength)(0: Byte),
      InitialTarget, Hash.hashSized(Array(0: Byte)), t, GenesisCreator)
  }

  override val MaxRollback: Int = settings.MaxRollback
  override val dataFolderOpt: Option[String] = settings.dataDirOpt


  override def isValid(block: PermaBlock): Boolean = {
    val f = block.consensusData

    parent(block).exists { parent =>
      val publicKey = blockGenerator(block).publicKey
      val puz = generatePuz(parent)
      val puzIsValid = f.puz.unsized sameElements puz.unsized
      val targetIsValid = f.target == calcTarget(parent)
      val ticketIsValid = true // TODO
      if (puzIsValid && targetIsValid && ticketIsValid)
        true
      else {
        log.warn(s"Invalid block: puz=$puzIsValid, target=$targetIsValid && ticket=$ticketIsValid")
        false
      }
    }
  }

  /**
   * Puzzle to a new generate block on top of $block
   */
  def generatePuz(block: PermaBlock): SizedDigest =
    Hash.hashSized(block.consensusData.puz.unsized ++ block.consensusData.ticket.s)

  def feesDistribution(block: PermaBlock): Map[PubKey, Long] =
    Map(blockGenerator(block) -> (miningReward(block) + transactionalModule.totalFee(block.transactionalData)))

  def blockScore(block: PermaBlock): BigInt = {
    val score = initialTargetPow - log2(block.consensusData.target)
    if (score > 0) score else 1
  }

  override def generateNextBlock(wallet: Wallet[_ <: PubKey, _ <: TM]): Future[Option[PermaBlock]] = Future {
    val parent = lastBlock
    val puz = generatePuz(parent)

    val privKey: PrivateKey25519Holder = ???
    val pubKey = privKey.publicCommitment
    val ticketTry = generate(privKey, puz)
    ticketTry match {
      case Success(ticket) =>
        val target = calcTarget(parent)
        if (validate(pubKey, puz, target, ticket, rootHash)) {
          val timestamp = NTP.correctedTime()
          val tData = transactionalModule.packUnconfirmed()
          val pId = parent.consensusData.blockId
          Some(PermaBlockBuilder.buildAndSign[TX,TData](tData, Version, timestamp, pId, target, puz, ticket, privKey))
        } else {
          None
        }
      case Failure(t) =>
        //TODO sync segments
        ???
    }
  }

  private val NoSig = Signature25519(Array[Byte]())

  private[consensus] def generate(acc: PrivateKey25519Holder, puz: Array[Byte]): Try[Ticket] = Try {

    val privateKey = acc.secret.unsized
    val publicKey = acc.publicCommitment.publicKey.unsized

    //scratch-off for the Local-POR lottery
    val s = Random.randomBytes(SSize)

    val sig0 = NoSig
    val r1 = calculateIndex(publicKey, (BigInt(1, Hash(puz ++ publicKey ++ s)) % PermaConstants.l).toInt)

    val proofs: IndexedSeq[PartialProof] = 1.to(PermaConstants.k).foldLeft(
      (r1, sig0, Seq[PartialProof]())
    ) {
      case ((ri, sig_prev, seq), _) =>
        val segment = authDataStorage.get(ri).get
        val hi = Hash(puz ++ publicKey ++ sig_prev.signature ++ segment.data)
        val sig = acc.sign(hi)
        val i = BigInt(1, Hash(puz ++ publicKey ++ sig.signature)).mod(PermaConstants.l).toInt
        val rNext = calculateIndex(publicKey, i)

        (rNext, sig, seq :+ PartialProof(sig, ri, segment))
    }._3.toIndexedSeq.ensuring(_.size == PermaConstants.k)

    Ticket(publicKey, s, proofs)
  }

  private[consensus] def validate(acc: PubKey,
                                  puz: Array[Byte],
                                  target: BigInt,
                                  t: Ticket,
                                  rootHash: Digest): Boolean = Try {
    val pk = acc.publicKey.unsized
    val proofs = t.proofs
    require(proofs.size == PermaConstants.k)
    require(t.s.length == SSize)

    val sigs = NoSig +: proofs.map(_.signature)
    val ris = proofs.map(_.segmentIndex)
    require(ris.head == calculateIndex(pk, (BigInt(1, Hash(puz ++ pk ++ t.s)) % PermaConstants.l).toInt))

    val partialProofsCheck = 1.to(PermaConstants.k).foldLeft(true) { case (partialResult, i) =>
      val segment = proofs(i - 1).segment
      val rc = calculateIndex(pk,
        BigInt(1, Hash(puz ++ pk ++ proofs(i - 1).signature.signature)).mod(PermaConstants.l).toInt)

      val check = segment.check(rootHash)
      check && {
        val message: Array[Byte] = Hash(puz ++ pk ++ sigs(i - 1).signature ++ segment.data)
        val sig = sigs(i)
        sig.isValid(acc, message)
      } && (ris.length == i || rc == ris(i))
    }
    partialProofsCheck && (ticketScore(t) < target)
  }.getOrElse(false)


  private[consensus] def ticketScore(t: Ticket): BigInt = if (t.proofs.nonEmpty) {
    BigInt(1, Hash(t.proofs.map(_.signature.signature).reduce(_ ++ _)))
  } else 0


  //calculate index of i-th segment
  private[consensus] def calculateIndex(pubKey: Array[Byte], i: Int): Long =
    BigInt(1, Hash(pubKey ++ BigInt(i).toByteArray)).mod(PermaConstants.n).toLong

  //TODO make configurable
  private def calcTarget(block: PermaBlock): BigInt = InitialTarget

  private def log2(i: BigInt): BigInt = BigDecimal(math.log(i.doubleValue()) / math.log(2)).toBigInt()

  override def parseBytes(bytes: Array[Byte]): Try[PermaConsensusBlockData] = ???
}

class NotEnoughSegments(ids: Seq[DataSegmentIndex]) extends Error
