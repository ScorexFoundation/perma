package scorex.perma.consensus

import akka.actor.ActorRef
import scorex.block.TransactionalData
import scorex.consensus.{ConsensusModule, ConsensusSettings, StoredBlockchain}
import scorex.crypto.authds.storage.KVStorage
import scorex.crypto.hash.FastCryptographicHash
import scorex.crypto.hash.FastCryptographicHash._
import scorex.network.NetworkController.SendToNetwork
import scorex.network.SendToRandom
import scorex.network.message.Message
import scorex.perma.network.GetSegmentsMessageSpec
import scorex.perma.settings.PermaConstants
import scorex.perma.settings.PermaConstants._
import scorex.settings.Settings
import scorex.settings.SizedConstants._
import scorex.transaction.Transaction
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.proof.Signature25519
import scorex.transaction.state.PrivateKey25519Holder
import scorex.transaction.wallet.Wallet
import scorex.utils.{Random, ScorexLogging}
import shapeless.Sized

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Data and functions related to a Permacoin consensus protocol
  */
class PermaConsensusModule[TX <: Transaction[PublicKey25519Proposition, TX]]
(rootHash: Sized[Array[Byte], Nat32],
 val settings: Settings with ConsensusSettings,
 networkController: ActorRef,
 blockchain: StoredBlockchain[PublicKey25519Proposition, PermaConsensusBlockData, TX, _ <: TransactionalData[TX]])
(implicit val authDataStorage: KVStorage[Long, PermaAuthData, _])
  extends ConsensusModule[PublicKey25519Proposition, PermaConsensusBlockData]
  with ScorexLogging {

  val BlockReward = 1000000
  val InitialTarget = PermaConstants.initialTarget
  val TargetRecalculation = PermaConstants.targetRecalculation
  val AvgDelay = PermaConstants.averageDelay
  implicit val Hash = FastCryptographicHash

  val SSize = 32
  val GenesisCreator = PublicKey25519Proposition(Sized.wrap(Array.fill(32)(0: Byte)))
  val Version: Byte = 2

  type PubKey = PublicKey25519Proposition


  override val BlockIdLength: Int = 64

  override def isValid(c: PermaConsensusBlockData): Boolean = {
    blockchain.blockById(c.parentId).exists { parent =>
      val publicKey = producers(c).head
      val puz = generatePuz(parent.consensusData)
      val puzIsValid = c.puz.unsized sameElements puz.unsized
      val targetIsValid = c.target == calcTarget(parent.consensusData)
      val ticketIsValid = validate(publicKey, c.puz.unsized, c.target, c.ticket, rootHash.unsized)
      if (puzIsValid && targetIsValid && ticketIsValid)
        true
      else {
        log.warn(s"Invalid block: puz=$puzIsValid, target=$targetIsValid && ticket=$ticketIsValid")
        false
      }
    }

  }

  override def producers(cdata: PermaConsensusBlockData): Seq[PubKey] = Seq(cdata.producer)

  override def generateCdata(wallet: Wallet[_ <: PubKey, _], time: DataSegmentIndex, txsId: Array[Byte]): Future[Option[PermaConsensusBlockData]] = Future {
    val parent = blockchain.lastBlock
    val puz = generatePuz(parent.consensusData)

    //TODO asInstanceOf
    val privKey: PrivateKey25519Holder = wallet.privateKeyAccount().asInstanceOf[PrivateKey25519Holder]
    val pubKey = privKey.publicCommitment
    val ticketTry = generate(privKey, puz)
    ticketTry match {
      case Success(ticket) =>
        val target = calcTarget(parent.consensusData)
        if (validate(pubKey, puz, target, ticket, rootHash)) {
          val pId =
            log.info("Build new block")
          Some(PermaConsensusBlockData.buildAndSign(parent.consensusData.id, txsId, target, puz, ticket, privKey))
        } else {
          log.info("Non-valid ticket")
          None
        }
      case Failure(t) =>
        val segmentIds = 1.to(PermaConstants.l).map(i => calculateIndex(pubKey.publicKey.unsized, i - 1))
          .filterNot(authDataStorage.containsKey)
        if (segmentIds.nonEmpty) {
          val msg = Message(GetSegmentsMessageSpec, Right(segmentIds), None)
          networkController ! SendToNetwork(msg, SendToRandom)
          log.warn(s"Failed to generate new ticket, ${segmentIds.length} segments required")
          throw new NotEnoughSegments(segmentIds)
        } else throw t
    }
  }

  override val genesisData: PermaConsensusBlockData = {
    val t = Ticket(GenesisCreator.publicKey, Array.fill(SSize)(0: Byte), IndexedSeq())
    val id = Array.fill(BlockIdLength)(0: Byte)
    val signature = Signature25519(Array.fill(BlockIdLength)(0: Byte))
    PermaConsensusBlockData(id, id, signature, InitialTarget, Hash.hashSized(Array(0: Byte)), t, GenesisCreator)
  }

  override val MaxRollback: Int = settings.MaxRollback

  /**
    * Puzzle to a new generate block on top of block
    */
  def generatePuz(consensusData: PermaConsensusBlockData): SizedDigest =
    Hash.hashSized(consensusData.puz.unsized ++ consensusData.ticket.s)

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

  private[consensus] def validate(acc: PublicKey25519Proposition,
                                  puz: Array[Byte],
                                  target: BigInt,
                                  t: Ticket,
                                  rootHash: Digest): Boolean = Try {
    /*
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
    */
    false
  }.getOrElse(false)


  private[consensus] def ticketScore(t: Ticket): BigInt = if (t.proofs.nonEmpty) {
    BigInt(1, Hash(t.proofs.map(_.signature.signature).reduce(_ ++ _)))
  } else 0


  //calculate index of i-th segment
  private[consensus] def calculateIndex(pubKey: Array[Byte], i: Int): Long =
    BigInt(1, Hash(pubKey ++ BigInt(i).toByteArray)).mod(PermaConstants.n).toLong

  private val targetBuf = TrieMap[String, BigInt]()

  private def calcTarget(consensusData: PermaConsensusBlockData): BigInt = {
    val currentTarget = consensusData.target
    val height = blockchain.heightOf(consensusData.id).get
    if (height % TargetRecalculation == 0 && height > TargetRecalculation) {
      def calc = {
        val lastAvgDuration: BigInt = blockchain.averageDelay(consensusData.parentId, TargetRecalculation).get
        val newTarget = currentTarget * lastAvgDuration / 1000 / AvgDelay
        log.debug(s"Height: $height, target:$newTarget vs $currentTarget, lastAvgDuration:$lastAvgDuration")
        newTarget
      }
      targetBuf.getOrElseUpdate(consensusData.encodedId, calc)
    } else {
      currentTarget
    }
  }

}

class NotEnoughSegments(ids: Seq[DataSegmentIndex]) extends Error
