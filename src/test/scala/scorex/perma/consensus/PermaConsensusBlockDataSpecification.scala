package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.perma.application.TestAppSupport
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.SecretGenerator25519
import scorex.utils._

class PermaConsensusBlockDataSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with Generators with TestAppSupport {


  val consensus = application.consensusModule

  ignore("PermaConsensusBlockData serialization roundtrip") {
    forAll(blockData) { bd: PermaConsensusBlockData =>
      val decoded = consensus.parseBytes(bd.bytes).get

    }
  }


}