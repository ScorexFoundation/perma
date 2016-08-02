package scorex.perma.application

import java.io.{RandomAccessFile, File}

import scorex.crypto.authds.merkle.versioned.MvStoreVersionedMerklizedIndexedSeq
import scorex.crypto.authds.storage.{MvStoreStorageType, KVStorage}
import scorex.crypto.encode.Base58
import scorex.crypto.hash.FastCryptographicHash
import scorex.perma.consensus.PermaAuthData
import scorex.perma.settings.{PermaConstants, PermaSettings}
import scorex.perma.storage.AuthDataStorage
import scorex.settings.Settings
import scorex.utils.ScorexLogging

trait TestAppSupport extends ScorexLogging {
  implicit val settings = new Settings with PermaSettings {
    lazy val rootHash: Array[Byte] = Base58.decode("13uSUANWHG7PaCac7i9QKDZriUNKXCi84UkS3ijGYTm1").get
    override lazy val filename = "settings-test.json"
  }

  log.info("Generating random data set")
  val treeDir = new File(settings.treeDir)
  treeDir.mkdirs()
  val datasetFile = settings.treeDir + "/data.file"
  new RandomAccessFile(datasetFile, "rw").setLength(PermaConstants.n * PermaConstants.segmentSize)
  log.info("Calculate tree")
  val tree = MvStoreVersionedMerklizedIndexedSeq.fromFile(datasetFile, Some(settings.treeDir), PermaConstants.segmentSize, FastCryptographicHash)

  log.info("Test tree")
  val index = PermaConstants.n - 3
  val leaf = tree.elementAndProof(index).get
  require(leaf.check(tree.rootHash)(FastCryptographicHash))

  log.info("Put ALL data to local storage")
  new File(settings.treeDir).mkdirs()
  implicit lazy val authDataStorage: KVStorage[Long, PermaAuthData, MvStoreStorageType] =
    new AuthDataStorage(Some(settings.authDataStorage))

  def addBlock(i: Long): Unit = {
    val p = tree.elementAndProof(i).get
    authDataStorage.set(i, new PermaAuthData(p.data, p.proof))
    if (i > 0) {
      addBlock(i - 1)
    }
  }

  addBlock(PermaConstants.n - 1)

  val rootHash = tree.rootHash
  val app = new TestApp(rootHash, authDataStorage)
  implicit val consensusModule = app.consensusModule
  implicit val transactionalModule = app.transactionModule
}
