package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.utils._
import io.circe.parser._

class PermaConsensusBlockDataSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with ScorexLogging with Generators {


  property("generate/validate roundtrip") {
    forAll(blockData) { bd: PermaConsensusBlockData =>
      val json = bd.json

      println(json.spaces4)
//      val decoded = decode[PermaConsensusBlockData](json.spaces4)
    }
  }

}