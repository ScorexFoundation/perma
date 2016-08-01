package scorex.perma.application

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{Matchers, WordSpecLike}
import scorex.network.HistorySynchronizer.GetStatus

class TestAppSpecification extends TestKit(ActorSystem("MySpec")) with WordSpecLike
with Matchers with TestAppSupport with ImplicitSender {

  "application" must {
    "apply genesis" in {
      application.checkGenesis()
    }
    "create historySynchronizer" in {
      application.historySynchronizer ! Unit
      application.historySynchronizer ! GetStatus
      expectMsg("syncing")
    }
  }


}