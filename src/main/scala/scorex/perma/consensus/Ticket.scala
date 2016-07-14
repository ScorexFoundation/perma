package scorex.perma.consensus

import io.circe.Json
import scorex.crypto.encode.Base58
import io.circe.syntax._

case class Ticket(publicKey: Array[Byte],
                  s: Array[Byte],
                  proofs: IndexedSeq[PartialProof]) {

  lazy val json: Json = (
    "publicKey" -> Base58.encode(publicKey),
    "s" -> Base58.encode(s),
    "proofs" -> proofs.map(_.json)
    ).asJson
}


