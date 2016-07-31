package scorex.perma.consensus

import io.circe.Json
import io.circe.syntax._
import scorex.crypto.encode.Base58
import scorex.serialization.{BytesParseable, BytesSerializable, JsonSerializable}

import scala.util.Try

case class Ticket(publicKey: Array[Byte],
                  s: Array[Byte],
                  proofs: IndexedSeq[PartialProof]) extends BytesSerializable with JsonSerializable {

  lazy val json: Json = Map(
    "publicKey" -> Base58.encode(publicKey).asJson,
    "s" -> Base58.encode(s).asJson,
    "proofs" -> proofs.map(_.json).asJson
  ).asJson

  override def bytes: Array[Byte] = {
    val p: Array[Byte] = proofs.map(p => arrayWithSize(p.bytes)).foldLeft(Array[Byte]())((a, b) => a ++ b)
    arrayWithSize(publicKey) ++ arrayWithSize(s) ++ p
  }
}

object Ticket extends BytesParseable[Ticket] {
  override def parseBytes(bytes: Array[Byte]): Try[Ticket] = Try {
    parseArraySizes(bytes) match {
      case publicKey :: s :: proofs =>
        Ticket(publicKey, s, proofs.map(b => PartialProof.parseBytes(b).get).toIndexedSeq)
      case _ => throw new Error("Failed to parse Ticket")
    }
  }
}
