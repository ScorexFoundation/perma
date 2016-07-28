package scorex.perma.consensus

import java.io.{File, RandomAccessFile}

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.authds.merkle.versioned.MvStoreVersionedMerklizedIndexedSeq
import scorex.crypto.authds.storage.{KVStorage, MvStoreStorageType}
import scorex.crypto.hash.FastCryptographicHash
import scorex.perma.application.TestApp
import scorex.perma.settings.{PermaConstants, PermaSettings}
import scorex.perma.storage.AuthDataStorage
import scorex.settings.Settings
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.SecretGenerator25519
import scorex.utils._

class PermaConsensusModuleSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with ScorexLogging {

  implicit val settings = new Settings with PermaSettings {
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
  val application = new TestApp(rootHash, authDataStorage)
  val consensus = application.consensusModule

  property("generate/validate roundtrip") {
    forAll { (seed: Array[Byte], puz: Array[Byte], wrongBytes: Array[Byte]) =>
      whenever(seed.nonEmpty && puz.nonEmpty && wrongBytes.nonEmpty && !wrongBytes.sameElements(puz)) {
        val keyPair = SecretGenerator25519.generateKeys(seed)
        val keyPair2 = SecretGenerator25519.generateKeys(randomBytes(32))
        val ticket = consensus.generate(keyPair, puz).get
        val publicKey: PublicKey25519Proposition = keyPair.publicCommitment
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe true
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket), ticket, rootHash) shouldBe false
        consensus.validate(keyPair2.publicCommitment, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
        consensus.validate(publicKey, wrongBytes, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, wrongBytes) shouldBe false
      }
    }
  }


}