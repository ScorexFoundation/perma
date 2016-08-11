package scorex.perma.application

import scorex.api.http.TransactionsApiRoute
import scorex.app.{Application, ApplicationVersion}
import scorex.crypto.authds.storage.KVStorage
import scorex.crypto.encode.Base58
import scorex.network.message.MessageSpec
import scorex.perma.consensus.{PermaAuthData, PermaConsensusBlockData, PermaConsensusModule}
import scorex.perma.settings.PermaSettings
import scorex.settings.Settings
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.{LagonakiTransaction, SimpleTransactionModule, SimplestTransactionalData}
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
  override implicit val transactionalModule = new SimpleTransactionModule(settings, networkController)
  val consensusModule = new PermaConsensusModule(Sized.wrap(rootHash), settings, transactionalModule)

  override val applicationName: String = "test"

  override def appVersion: ApplicationVersion = ApplicationVersion(0, 0, 0)

  override val apiRoutes = Seq(TransactionsApiRoute(transactionalModule, settings))
  override val apiTypes = Seq(typeOf[TransactionsApiRoute])

}
