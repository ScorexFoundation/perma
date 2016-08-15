package scorex.perma.application

import scorex.api.http.TransactionsApiRoute
import scorex.app.{Application, ApplicationVersion}
import scorex.crypto.authds.storage.KVStorage
import scorex.crypto.encode.Base58
import scorex.network.message.MessageSpec
import scorex.perma.consensus.{PermaAuthData, PermaConsensusBlockData, PermaConsensusModule}
import scorex.perma.settings.PermaSettings
import scorex.serialization.BytesParseable
import scorex.settings.Settings
import scorex.transaction._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.database.LagonakiUnconfirmedTransactionsDatabase
import shapeless.Sized

import scala.reflect.runtime.universe._
import scala.util.Failure

class TestApp(rootHash: Array[Byte], implicit val authDataStorage: KVStorage[Long, PermaAuthData, _]) extends {
  override protected val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq()

} with Application {
  override type CD = PermaConsensusBlockData
  override type P = PublicKey25519Proposition
  override type TX = LagonakiTransaction
  override type TD = SimplestTransactionalData

  override implicit val settings = new Settings with PermaSettings {
    override lazy val filename = "settings-test.json"
    lazy val rootHash: Array[Byte] = Base58.decode("13uSUANWHG7PaCac7i9QKDZriUNKXCi84UkS3ijGYTm1").get
  }
  override val transactionalParser: BytesParseable[SimplestTransactionalData] = SimplestTransactionalData
  override val consensusParser: BytesParseable[PermaConsensusBlockData] = PermaConsensusBlockData
  val pool = new LagonakiUnconfirmedTransactionsDatabase(settings.dataDirOpt)

  override val stateHolder = new LagonakiStateHolder(settings.dataDirOpt)

  override val blockValidator = new LagonakiBlockValidator(settings.rootHash)
  override val rewardCalculator = LagonakiRewardCalculator

  override implicit val transactionalModule = new SimpleTransactionModule(settings, stateHolder.mempool, stateHolder.state)
  override implicit val consensusModule = new PermaConsensusModule(Sized.wrap(rootHash), settings, networkController, stateHolder.history)

  override val applicationName: String = "test"

  override def appVersion: ApplicationVersion = ApplicationVersion(0, 0, 0)

  override val apiRoutes = Seq(TransactionsApiRoute(pool, settings))
  override val apiTypes = Seq(typeOf[TransactionsApiRoute])

  def checkGenesis(): Unit = {
    if (stateHolder.history.isEmpty) {
      val genesisBlock: BType = scorex.block.Block.genesis[P, TX, TD, CD](settings.genesisTimestamp)
      val changes = rewardCalculator.changes(genesisBlock, stateHolder.state)
      stateHolder.appendBlock(genesisBlock, changes) match {
        case Failure(e) => log.error("Failed to append genesis block", e)
        case _ => log.info("Genesis block has been added to the state")
      }
    }
  }.ensuring(stateHolder.history.height() >= 1)

}
