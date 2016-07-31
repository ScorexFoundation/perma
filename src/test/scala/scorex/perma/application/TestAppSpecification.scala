package scorex.perma.application

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.SecretGenerator25519
import scorex.utils._

class TestAppSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with TestAppSupport {

  property("checkGenesis()") {
    application.checkGenesis()
  }


}