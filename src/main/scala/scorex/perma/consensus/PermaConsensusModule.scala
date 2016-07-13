package scorex.perma.consensus

import akka.actor.ActorRef
import scorex.account.{Account, PrivateKeyAccount, PublicKeyAccount}
import scorex.block.{TransactionalData, Block, BlockField}
import scorex.consensus.nxt.NxtLikeConsensusBlockData
import scorex.consensus.{StoredBlockchain, ConsensusModule}
import scorex.consensus.nxt.NxtLikeConsensusBlockData
import scorex.crypto.EllipticCurveImpl
import scorex.crypto.ads.merkle.AuthDataBlock
import scorex.crypto.hash.CryptographicHash.Digest
import scorex.crypto.hash.FastCryptographicHash
import scorex.crypto.hash.FastCryptographicHash.Digest
import scorex.crypto.singing.SigningFunctions.{PrivateKey, PublicKey}
import scorex.network.NetworkController.SendToNetwork
import scorex.network.SendToRandom
import scorex.network.message.Message
import scorex.perma.network.GetSegmentsMessageSpec
import scorex.perma.settings.PermaConstants
import scorex.perma.settings.PermaConstants._
import scorex.settings.SizedConstants._
import scorex.storage.Storage
import scorex.transaction.TransactionModule
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.utils.{NTP, ScorexLogging, randomBytes}
import shapeless.Sized

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scorex.transaction._

/**
 * Data and functions related to a Permacoin consensus protocol
 */
class PermaConsensusModule[TX <: Transaction[PublicKey25519Proposition, TX], TData <: TransactionalData[TX]]
(rootHash: Sized[Array[Byte], Nat32], networkControllerOpt: Option[ActorRef] = None)
  extends ConsensusModule[PublicKey25519Proposition, TX, TData, PermaConsensusBlockData]
  with StoredBlockchain[PublicKey25519Proposition, PermaConsensusBlockData, TX, TData]
  with ScorexLogging {

  val BlockReward = 1000000
  val InitialTarget = PermaConstants.initialTarget
  val initialTargetPow: BigInt = log2(InitialTarget)
  val TargetRecalculation = PermaConstants.targetRecalculation
  val AvgDelay = PermaConstants.averageDelay
  val Hash = FastCryptographicHash

  val GenesisCreator = PublicKey25519Proposition(Sized.wrap(Array.fill(32)(0: Byte)))
  val Version: Byte = 2

  type PermaBlock = Block[PublicKey25519Proposition, TData, PermaConsensusBlockData]

  private def miningReward(block: PermaBlock): Long = BlockReward

  private def blockGenerator(block: PermaBlock): PublicKey25519Proposition = block.consensusData.producer

  def isValid[TT](block: PermaBlock)(implicit transactionModule: TransactionModule[TT]): Boolean = {
    val f = consensusBlockData(block)

    parent(block).exists { parent =>
      val publicKey = blockGenerator(block).publicKey
      val puz = generatePuz(parent)
      val puzIsValid = f.puz.unsized sameElements puz.unsized
      val targetIsValid = f.target == calcTarget(parent)
      val ticketIsValid = validate(publicKey, f.puz, f.target, f.ticket, rootHash)
      if (puzIsValid && targetIsValid && ticketIsValid)
        true
      else {
        log.warn(s"Invalid block: puz=$puzIsValid, target=$targetIsValid && ticket=$ticketIsValid")
        false
      }
    }
  }

  def feesDistribution(block: PermaBlock): Map[Account, Long] =
    Map(blockGenerator(block) -> (miningReward(block) + block.transactions.map(_.fee).sum))

  def generators(block: PermaBlock): Seq[Account] = Seq(blockGenerator(block))

  def blockScore(block: PermaBlock)(implicit transactionModule: TransactionModule[_]): BigInt = {
    val score = initialTargetPow - log2(consensusBlockData(block).target)
    if (score > 0) score else 1
  }

  def generateNextBlock[TT](account: PrivateKeyAccount)
                           (implicit transactionModule: TransactionModule[TT]): Future[Option[Block]] = Future {
    val parent = transactionModule.blockStorage.history.lastBlock
    val puz = generatePuz(parent)

    val keyPair = (account.privateKey, account.publicKey)
    val ticketTry = generate(keyPair, puz)
    ticketTry match {
      case Success(ticket) =>
        val target = calcTarget(parent)
        if (validate(keyPair._2, puz, target, ticket, rootHash)) {
          val timestamp = NTP.correctedTime()
          log.info("Build Block: Valid ticket generated")
          val consData = PermaConsensusBlockData(target, puz, ticket)
          log.info("Build Block: packed consensus data")
          val transData = transactionModule.packUnconfirmed()
          log.info("Build Block: packed transaction data")
          val blockTry = Try(Block.buildAndSign(Version,
            timestamp,
            parent.uniqueId,
            consData,
            transData,
            account))
          blockTry.recoverWith {
            case e =>
              log.error("Failed to build block:", e)
              Failure(e)
          }.toOption
        } else {
          None
        }
      case Failure(t) =>
        val segmentIds: Seq[DataSegmentIndex] = 1.to(PermaConstants.l).map(i => calculateIndex(account.publicKey, i - 1))
          .filterNot(authDataStorage.containsKey)
        if (segmentIds.nonEmpty) {
          val msg = Message(GetSegmentsMessageSpec, Right(segmentIds), None)
          if (networkControllerOpt.isDefined) {
            networkControllerOpt.get ! SendToNetwork(msg, SendToRandom)
          }
          log.warn(s"Failed to generate new ticket, ${segmentIds.length} segments required")
          throw new NotEnoughSegments(segmentIds)
        } else throw t
    }
  }

  override def consensusBlockData(block: PermaBlock): PermaConsensusBlockData = block.consensusDataField.value match {
    case b: PermaConsensusBlockData => b
    case m => throw new AssertionError(s"Only PermaLikeConsensusBlockData is available, $m given")
  }

  override def parseBytes(bytes: Array[Byte]): Try[PermaConsensusBlockField] =
    PermaConsensusBlockField.parse(bytes)

  override def genesisData: PermaConsensusBlockField =
    PermaConsensusBlockField(PermaConsensusBlockData(
      InitialTarget,
      Array.fill(PermaConsensusBlockField.PuzLength)(0: Byte),
      Ticket(GenesisCreator.publicKey, Array.fill(PermaConsensusBlockField.SLength)(0: Byte), IndexedSeq())
    ))

  override def formBlockData(data: PermaConsensusBlockData): BlockField[PermaConsensusBlockData] =
    PermaConsensusBlockField(data)

  /**
   * Puzzle to a new generate block on top of $block
   */
  def generatePuz(block: PermaBlock): Digest = Hash(consensusBlockData(block).puz ++ consensusBlockData(block).ticket.s)

  private val NoSig = Array[Byte]()

  private[consensus] def validate(publicKey: PublicKey,
                                  puz: Array[Byte],
                                  target: BigInt,
                                  t: Ticket,
                                  rootHash: Digest): Boolean = Try {
    val proofs = t.proofs
    require(proofs.size == PermaConstants.k)
    require(t.s.length == SSize)

    val sigs = NoSig +: proofs.map(_.signature)
    val ris = proofs.map(_.segmentIndex)
    require(ris(0) == calculateIndex(publicKey, (BigInt(1, Hash(puz ++ publicKey ++ t.s)) % PermaConstants.l).toInt))

    val partialProofsCheck = 1.to(PermaConstants.k).foldLeft(true) { case (partialResult, i) =>
      val segment = proofs(i - 1).segment
      val rc = calculateIndex(publicKey,
        BigInt(1, Hash(puz ++ publicKey ++ proofs(i - 1).signature)).mod(PermaConstants.l).toInt)

      segment.check(ris(i - 1), rootHash)() && {
        val hi = Hash(puz ++ publicKey ++ sigs(i - 1) ++ segment.data)
        EllipticCurveImpl.verify(sigs(i), hi, publicKey)
      } && (ris.length == i || rc == ris(i))
    }
    partialProofsCheck && (ticketScore(t) < target)
  }.getOrElse(false)

  private[consensus] def ticketScore(t: Ticket): BigInt = if (t.proofs.nonEmpty) {
    BigInt(1, Hash(t.proofs.map(_.signature).reduce(_ ++ _)))
  } else 0

  private[consensus] def generate(keyPair: (PrivateKey, PublicKey), puz: Array[Byte]): Try[Ticket] = Try {

    val (privateKey, publicKey) = keyPair

    //scratch-off for the Local-POR lottery
    val s = randomBytes(SSize)

    val sig0 = NoSig
    val r1 = calculateIndex(publicKey, (BigInt(1, Hash(puz ++ publicKey ++ s)) % PermaConstants.l).toInt)

    val proofs: IndexedSeq[PartialProof] = 1.to(PermaConstants.k).foldLeft(
      (r1, sig0, Seq[PartialProof]())
    ) {
      case ((ri, sig_prev, seq), _) =>
        val segment = authDataStorage.get(ri).get
        val hi = Hash(puz ++ publicKey ++ sig_prev ++ segment.data)
        val sig = EllipticCurveImpl.sign(privateKey, hi)
        val rNext = calculateIndex(publicKey, BigInt(1, Hash(puz ++ publicKey ++ sig)).mod(PermaConstants.l).toInt)

        (rNext, sig, seq :+ PartialProof(sig, ri, segment))
    }._3.toIndexedSeq.ensuring(_.size == PermaConstants.k)

    Ticket(publicKey, s, proofs)
  }

  //calculate index of i-th segment
  private[consensus] def calculateIndex(pubKey: PublicKey, i: Int): Long =
    BigInt(1, Hash(pubKey ++ BigInt(i).toByteArray)).mod(PermaConstants.n).toLong

  private val targetBuf = TrieMap[String, BigInt]()

  private def calcTarget(block: PermaBlock)(implicit transactionModule: TransactionModule[_]): BigInt = {
    val trans = transactionModule.blockStorage.history
    val currentTarget = consensusBlockData(block).target
    val height = trans.heightOf(block).get
    if (height % TargetRecalculation == 0 && height > TargetRecalculation) {
      def calc = {
        val lastAvgDuration: BigInt = trans.averageDelay(block, TargetRecalculation).get
        val newTarget = currentTarget * lastAvgDuration / 1000 / AvgDelay
        log.debug(s"Height: $height, target:$newTarget vs $currentTarget, lastAvgDuration:$lastAvgDuration")
        newTarget
      }
      targetBuf.getOrElseUpdate(block.encodedId, calc)
    } else {
      currentTarget
    }
  }

  private def log2(i: BigInt): BigInt = BigDecimal(math.log(i.doubleValue()) / math.log(2)).toBigInt()
}

class NotEnoughSegments(ids: Seq[DataSegmentIndex]) extends Error
