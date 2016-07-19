package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.utils._
import io.circe.parser._
import io.circe.syntax._

class PermaConsensusBlockDataSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with ScorexLogging with Generators {


  property("generate/validate roundtrip") {
    forAll(blockData) { bd: PermaConsensusBlockData =>

      println(bd.json.spaces4)
      assert(false)
//      val decoded = decode[PermaConsensusBlockData](json.spaces4)
    }
  }

}