package scorex

import org.scalatest.Suites
import scorex.perma.application.TestAppSpecification
import scorex.perma.consensus.{PermaConsensusJsonSerializationSpecification, PermaConsensusModuleSpecification}
import scorex.perma.network.SegmentsMessageSpecification
import scorex.perma.storage.AuthDataStorageSpecification

class PermaTestSuite extends Suites(
  new TestAppSpecification,
  new AuthDataStorageSpecification,
  new SegmentsMessageSpecification,
  new PermaConsensusModuleSpecification,
  new PermaConsensusJsonSerializationSpecification
)
