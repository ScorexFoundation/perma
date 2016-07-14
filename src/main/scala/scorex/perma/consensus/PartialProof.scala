package scorex.perma.consensus

import io.circe.Json
import scorex.crypto.encode.Base58
import scorex.transaction.proof.Signature25519
import io.circe.syntax._

case class PartialProof(signature: Signature25519, segmentIndex: Long, segment: PermaAuthData) {
  lazy val json: Json = {
    "signature" -> Base58.encode(signature.signature)
    "segmentIndex" -> segmentIndex
    "segment" -> segment.json
  }.asJson
}
