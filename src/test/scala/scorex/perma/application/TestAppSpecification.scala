package scorex.perma.application

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import scorex.block.Block
import scorex.network.HistorySynchronizer.GetStatus
import scorex.utils.NetworkTime

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure

class TestAppSpecification extends TestKit(ActorSystem("MySpec")) with WordSpecLike
with Matchers with TestAppSupport with ImplicitSender {

  "application" must {
    "serialize/parse genesis" in {
      val genesis = Block.genesis[app.P, app.TX, app.TD, app.CD](settings.genesisTimestamp)
      val parsed = Block.parseBytes[app.P, app.TX, app.TD, app.CD](genesis.bytes)(app.consensusParser, app.transactionalParser)
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
      app.stateHolder.history.height() should be >= 1
      app.stateHolder.history.lastBlocks(1).length shouldBe 1
      app.stateHolder.history.lastBlock
    }
    "create historySynchronizer" in {
      app.historySynchronizer ! Unit
      app.historySynchronizer ! GetStatus
      expectMsg("syncing")
    }
    "generate next block" in {
      app.checkGenesis()
      val timestamp = NetworkTime.time()
      val tData = transactionalModule.generateTdata(timestamp)
      val cFuture = consensusModule.generateCdata(transactionalModule.wallet, timestamp, tData.id)
      Await.result(cFuture, 5.second) match {
        case Some(cData) =>
        case _ => throw new Error("block was not generated")
      }
    }
  }
}