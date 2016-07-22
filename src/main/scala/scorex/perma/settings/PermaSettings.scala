package scorex.perma.settings

import java.io.File

import scorex.consensus.ConsensusSettings
import scorex.crypto.encode.Base58

import scala.util.Try

trait PermaSettings extends ConsensusSettings {

  lazy val rootHash: Array[Byte] = Base58.decode("13uSUANWHG7PaCac7i9QKDZriUNKXCi84UkS3ijGYTm1").get

  lazy val isTrustedDealer =
    Try(settingsJSON.get("perma").get.asObject.get.toMap.get("isTrustedDealer").get.asBoolean.get).getOrElse(false)

  lazy val treeDir = {
    val dir = settingsJSON.get("perma").get.asObject.get.toMap.get("treeDir").get.asString.get
    new File(dir).mkdirs()
    dir
  }

  lazy val authDataStorage = treeDir +
    Try(settingsJSON.get("perma").get.asObject.get.toMap.get("authDataStorage").get.asString.get).getOrElse(false)

}
