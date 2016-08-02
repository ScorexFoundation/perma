package scorex.perma.application

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import scorex.block.Block
import scorex.network.HistorySynchronizer.GetStatus

import scala.util.Failure

class TestAppSpecification extends TestKit(ActorSystem("MySpec")) with WordSpecLike
with Matchers with TestAppSupport with ImplicitSender {

  "application" must {
    "serialize/parse genesis" in {
      val genesis = Block.genesis[app.P, app.TX, app.TData, app.CData](settings.genesisTimestamp)
      val parsed = Block.parse[app.P, app.TX, app.TData, app.CData](genesis.bytes)(consensusModule, transactionalModule)
      parsed match {
        case Failure(e) => throw e
        case _ =>
      }
      parsed.get.bytes shouldEqual genesis.bytes
    }
    "apply genesis" in {
      app.checkGenesis()
    }
    "get last block" in {
      app.consensusModule.height() should be >= 1
      app.consensusModule.lastBlocks(1).length shouldBe 1
      app.consensusModule.lastBlock
    }
    "create historySynchronizer" in {
      app.historySynchronizer ! Unit
      app.historySynchronizer ! GetStatus
      expectMsg("syncing")
    }
  }


}