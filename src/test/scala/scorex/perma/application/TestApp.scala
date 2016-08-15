package scorex.perma.application

import scorex.{block, NodeStateHolder}
import scorex.api.http.TransactionsApiRoute
import scorex.app.{Application, ApplicationVersion}
import scorex.block.{StateChanges, StateChangesCalculator, BlockValidator}
import scorex.consensus.{History, StoredBlockchain}
import scorex.crypto.authds.storage.KVStorage
import scorex.crypto.encode.Base58
import scorex.network.message.MessageSpec
import scorex.perma.consensus.{PermaAuthData, PermaConsensusBlockData, PermaConsensusModule, PermaValidator}
import scorex.perma.settings.PermaSettings
import scorex.serialization.BytesParseable
import scorex.settings.Settings
import scorex.transaction._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.database.LagonakiUnconfirmedTransactionsDatabase
import scorex.transaction.state.{MinimalState, PersistentLagonakiState}
import shapeless.Sized

import scala.reflect.runtime.universe._

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
  val pool = new LagonakiUnconfirmedTransactionsDatabase(None)

  override val stateHolder: NodeStateHolder[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData] = {
    val blockchain = new StoredBlockchain[PublicKey25519Proposition, PermaConsensusBlockData, LagonakiTransaction, SimplestTransactionalData] {
      override val dataFolderOpt: Option[String] = None

      override val transactionalParser: BytesParseable[SimplestTransactionalData] = SimplestTransactionalData
      override val consensusParser: BytesParseable[PermaConsensusBlockData] = PermaConsensusBlockData
    }

    new NodeStateHolder[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData] {
      override protected val globalState: (MinimalState[PublicKey25519Proposition, LagonakiTransaction], History[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData], MemoryPool[LagonakiTransaction]) =
        (new PersistentLagonakiState(None), blockchain, pool)

      override def stableState: (MinimalState[PublicKey25519Proposition, LagonakiTransaction], History[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData], MemoryPool[LagonakiTransaction]) =
        globalState
    }
  }

  override val blockValidator: BlockValidator[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData] = {
    val txValidator = SimpleTransactionValidator
    val cValidator = new PermaValidator(rootHash)
    new BlockValidator[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData](txValidator, cValidator)
  }
  override val rewardCalculator: StateChangesCalculator[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData] = {
    new StateChangesCalculator[PublicKey25519Proposition, LagonakiTransaction, SimplestTransactionalData, PermaConsensusBlockData] {
      override def changes(block: scorex.block.Block[PublicKey25519Proposition, SimplestTransactionalData, PermaConsensusBlockData], state: MinimalState[PublicKey25519Proposition, LagonakiTransaction]): StateChanges[PublicKey25519Proposition] = StateChanges(Set(), Set())
    }
  }


  override implicit val transactionalModule = new SimpleTransactionModule(settings, stateHolder.mempool, stateHolder.state)
  val consensusModule = new PermaConsensusModule(Sized.wrap(rootHash), settings, networkController, stateHolder.history)

  override val applicationName: String = "test"

  override def appVersion: ApplicationVersion = ApplicationVersion(0, 0, 0)

  override val apiRoutes = Seq(TransactionsApiRoute(pool, settings))
  override val apiTypes = Seq(typeOf[TransactionsApiRoute])

}
