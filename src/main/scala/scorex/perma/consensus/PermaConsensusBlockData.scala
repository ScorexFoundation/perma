package scorex.perma.consensus

import play.api.libs.functional.syntax._
import play.api.libs.json._
import scorex.settings.SizedConstants._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.utils.JsonSerialization
import shapeless.Sized

case class PermaConsensusBlockData(target: BigInt, puz: Sized[Array[Byte], Nat32], ticket: Ticket, producer: PublicKey25519Proposition)

/*
object PermaConsensusBlockData extends JsonSerialization {

  implicit val writes: Writes[PermaConsensusBlockData] = (
    (JsPath \ "difficulty").write[BigInt] and
      (JsPath \ "puz").write[Bytes] and
      (JsPath \ "ticket").write[Ticket]
    ) (unlift(PermaConsensusBlockData.unapply))

  implicit val reads: Reads[PermaConsensusBlockData] = (
    (JsPath \ "difficulty").read[BigInt] and
      (JsPath \ "puz").read[Bytes] and
      (JsPath \ "ticket").read[Ticket]
    ) (PermaConsensusBlockData.apply _)

}
*/
