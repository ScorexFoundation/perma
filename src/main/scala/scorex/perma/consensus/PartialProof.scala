package scorex.perma.consensus

import com.google.common.primitives.{Ints, Longs}
import io.circe.Json
import io.circe.syntax._
import scorex.crypto.encode.Base58
import scorex.serialization.{BytesParseable, BytesSerializable, JsonSerializable}
import scorex.transaction.proof.Signature25519

import scala.util.Try

case class PartialProof(signature: Signature25519, segmentIndex: Long, segment: PermaAuthData) extends JsonSerializable
with BytesSerializable {
  lazy val json: Json = Map(
    "signature" -> Base58.encode(signature.signature).asJson,
    "segmentIndex" -> segmentIndex.asJson,
    "segment" -> segment.json
  ).asJson

  override def bytes: Array[Byte] = {
    Longs.toByteArray(segmentIndex) ++ arrayWithSize(signature.signature) ++ segment.bytes
  }
}

object PartialProof extends BytesParseable[PartialProof] {
  override def parseBytes(bytes: Array[Byte]): Try[PartialProof] = Try {
    val i = Longs.fromByteArray(bytes.slice(0, 8))
    val sigSize = Ints.fromByteArray(bytes.slice(8, 12))
    val sig = Signature25519(bytes.slice(12, 12 + sigSize))
    val segment = PermaAuthData.parseBytes(bytes.slice(12 + sigSize, bytes.length)).get
    PartialProof(sig, i, segment)
  }
}