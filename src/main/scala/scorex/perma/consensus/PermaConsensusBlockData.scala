package scorex.perma.consensus

import io.circe.{Decoder, Json}
import scorex.block.ConsensusData
import scorex.crypto.encode.Base58
import scorex.settings.SizedConstants._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import shapeless.Sized
import io.circe.syntax._

case class PermaConsensusBlockData(parentId: Array[Byte],
                                   signature: Array[Byte],
                                   target: BigInt,
                                   puz: Sized[Array[Byte], Nat32],
                                   ticket: Ticket,
                                   producer: PublicKey25519Proposition) extends ConsensusData {
  override val BlockIdLength: Int = 64

  lazy val blockId = signature

  override lazy val json: Json = Map(
    "parentId" -> Base58.encode(parentId).asJson,
    "signature" -> Base58.encode(signature).asJson,
    "target" -> target.asJson,
    "puz" -> Base58.encode(puz.unsized).asJson,
    "ticket" -> ticket.json,
    "producer" -> producer.address.asJson
  ).asJson

  override val bytes: Array[Byte] = Array()
}

object PermaConsensusBlockData {

  Decoder

}