package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.utils._
import io.circe.parser._
import io.circe.syntax._

class PermaConsensusJsonSerializationSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with ScorexLogging with Generators {

  property("PermaAuthData JSON serialization roundtrip") {
    forAll(segmentGen) { ad: PermaAuthData =>

      val decoded = decode[PermaAuthData](ad.json.spaces4)
      decoded.isRight shouldBe true
      decoded map { d: PermaAuthData =>
        d.data shouldEqual ad.data
        d.proof.index shouldEqual ad.proof.index

      }
    }
  }


  ignore("PermaConsensusBlockData JSON serialization roundtrip") {
    forAll(blockData) { bd: PermaConsensusBlockData =>
      //      val decoded = decode[PermaConsensusBlockData](json.spaces4)
    }
  }

}