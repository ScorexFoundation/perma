package scorex

import org.scalatest.Suites
import scorex.perma.consensus.{PermaConsensusBlockDataSpecification, PermaConsensusModuleSpecification}
import scorex.perma.network.SegmentsMessageSpecification
import scorex.perma.storage.AuthDataStorageSpecification

class PermaTestSuite extends Suites(
  new AuthDataStorageSpecification,
  new SegmentsMessageSpecification,
  new PermaConsensusModuleSpecification,
  new PermaConsensusBlockDataSpecification
)
