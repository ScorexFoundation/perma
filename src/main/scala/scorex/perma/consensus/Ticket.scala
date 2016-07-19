package scorex.perma.consensus

import io.circe.Json
import scorex.crypto.encode.Base58
import io.circe.syntax._

case class Ticket(publicKey: Array[Byte],
                  s: Array[Byte],
                  proofs: IndexedSeq[PartialProof]) {

  lazy val json: Json = Map(
    "publicKey" -> Base58.encode(publicKey).asJson,
    "s" -> Base58.encode(s).asJson,
    "proofs" -> proofs.map(_.json).asJson
    ).asJson
}


