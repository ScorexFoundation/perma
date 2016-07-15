package scorex.perma.consensus

import io.circe.Json
import io.circe.syntax._
import scorex.crypto.authds.merkle.{MerkleAuthData, MerklePath}
import scorex.crypto.encode.Base58
import scorex.crypto.hash.CryptographicHash

class PermaAuthData(data: Array[Byte], proof: MerklePath[CryptographicHash])
  extends MerkleAuthData[CryptographicHash](data, proof) {
  lazy val json: Json = {
    "data" -> Base58.encode(data)
    "proof" -> {
      "index" -> proof.index
      "hashes" -> proof.hashes.map(Base58.encode)
    }.asJson
  }.asJson
}

