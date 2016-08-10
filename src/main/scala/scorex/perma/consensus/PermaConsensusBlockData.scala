package scorex.perma.consensus

import io.circe.Json
import io.circe.syntax._
import scorex.block.ConsensusData
import scorex.block.ConsensusData.BlockId
import scorex.crypto.encode.Base58
import scorex.serialization.BytesParseable
import scorex.settings.SizedConstants.Nat32
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.proof.Signature25519
import scorex.transaction.state.PrivateKey25519Holder
import shapeless.Sized

import scala.util.Try

case class PermaConsensusBlockData(parentId: Array[Byte],
                                   transactionalId: Array[Byte],
                                   signature: Signature25519,
                                   target: BigInt,
                                   puz: Sized[Array[Byte], Nat32],
                                   ticket: Ticket,
                                   producer: PublicKey25519Proposition) extends ConsensusData {
  override val BlockIdLength: Int = 64

  lazy val id: BlockId = signature.signature

  override lazy val json: Json = Map(
    "parentId" -> Base58.encode(parentId).asJson,
    "transactionalId" -> Base58.encode(transactionalId).asJson,
    "signature" -> Base58.encode(signature.signature).asJson,
    "target" -> target.asJson,
    "puz" -> Base58.encode(puz.unsized).asJson,
    "ticket" -> ticket.json,
    "producer" -> producer.address.asJson
  ).asJson

  override val bytes: Array[Byte] = {
    arrayWithSize(parentId) ++ arrayWithSize(transactionalId) ++ arrayWithSize(signature.signature) ++
      arrayWithSize(target.toByteArray) ++ arrayWithSize(puz.unsized) ++ arrayWithSize(ticket.bytes) ++
      arrayWithSize(producer.bytes)
  }

}

object PermaConsensusBlockData extends BytesParseable[PermaConsensusBlockData] {
  override def parseBytes(bytes: Array[Byte]): Try[PermaConsensusBlockData] = Try {
    parseArraySizes(bytes) match {
      case parentId :: transactionalId :: signature :: targetBytes :: puz :: ticketBytes :: producerBytes :: Nil =>
        val target = BigInt(targetBytes)
        val ticket = Ticket.parseBytes(ticketBytes).get
        val producer = PublicKey25519Proposition(Sized.wrap(producerBytes))
        PermaConsensusBlockData(parentId, transactionalId, Signature25519(signature), target, Sized.wrap(puz), ticket, producer)
      case _ => throw new Error("parseArraySizes failure")
    }
  }

  def buildAndSign(parentId: Array[Byte], txsId: Array[Byte], target: BigInt, puz: Sized[Array[Byte], Nat32],
                   ticket: Ticket, producer: PrivateKey25519Holder): PermaConsensusBlockData = {
    val eSig = Signature25519(Array())
    val toSign = PermaConsensusBlockData(parentId, txsId, eSig, target, puz, ticket, producer.publicCommitment).bytes
    val signature = producer.sign(toSign)
    PermaConsensusBlockData(parentId, txsId, signature, target, puz, ticket, producer.publicCommitment)
  }
}