package scorex.perma.consensus

import cats.data.Xor
import io.circe._
import io.circe.syntax._
import scorex.crypto.authds.merkle.{MerkleAuthData, MerklePath}
import scorex.crypto.encode.Base58
import scorex.crypto.hash.FastCryptographicHash
import scorex.serialization.{BytesParseable, BytesSerializable, JsonSerializable}

import scala.util.Try

class PermaAuthData(data: Array[Byte], proof: MerklePath[FastCryptographicHash.type])
  extends MerkleAuthData[FastCryptographicHash.type](data, proof) with JsonSerializable with BytesSerializable {
  lazy val json: Json = Map(
    "data" -> Base58.encode(data).asJson,
    "proof" -> Map(
      "index" -> proof.index.asJson,
      "hashes" -> proof.hashes.map(Base58.encode).asJson
    ).asJson
  ).asJson

}

object PermaAuthData extends BytesParseable[PermaAuthData] {


  override def parseBytes(bytes: Array[Byte]): Try[PermaAuthData] = Try {
    val m: MerkleAuthData[FastCryptographicHash.type] = MerkleAuthData.decode(bytes).get
    new PermaAuthData(m.data, m.proof)
  }

  /**
    * @group Decoding
    */
  implicit final val decodeJson: Decoder[PermaAuthData] = new Decoder[PermaAuthData] {
    final def apply(c: HCursor): Xor[DecodingFailure, PermaAuthData] = c.focus.asObject match {
      case Some(m: JsonObject) =>
        val map = m.toMap
        map.get("data").flatMap(d => d.asString.flatMap(i => Base58.decode(i).toOption)) match {
          case Some(data) => map.get("proof") match {
            case Some(proof) =>
              proof.asObject.map(_.toMap) match {
                case Some(po) =>
                  po.get("index").flatMap { indexJs =>
                    po.get("hashes").flatMap { h =>
                      Try {
                        val index: Long = indexJs.asNumber.get.toLong.get
                        val hashes = h.asArray.get.map(_.asString.get).map(s => Base58.decode(s).get)
                        new PermaAuthData(data, MerklePath(index, hashes))
                      }.toOption
                    }
                  } match {
                    case Some(pad) => Xor.right(pad)
                    case _ => Xor.left(DecodingFailure("Failed to parse proof", c.history))
                  }
                case None => Xor.left(DecodingFailure("Proof is not an object", c.history))
              }
            case None => Xor.left(DecodingFailure("Proof is undefined", c.history))
          }
          case None => Xor.left(DecodingFailure("Failed to parse data", c.history))
        }
      case _ =>
        Xor.left(DecodingFailure("Not an object", c.history))
    }
  }
}

