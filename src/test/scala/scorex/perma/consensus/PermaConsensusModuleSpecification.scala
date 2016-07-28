package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.perma.application.TestAppSupport
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.SecretGenerator25519
import scorex.utils._

class PermaConsensusModuleSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with TestAppSupport {


  val consensus = application.consensusModule

  property("generate/validate roundtrip") {
    forAll { (seed: Array[Byte], puz: Array[Byte], wrongBytes: Array[Byte]) =>
      whenever(seed.nonEmpty && puz.nonEmpty && wrongBytes.nonEmpty && !wrongBytes.sameElements(puz)) {
        val keyPair = SecretGenerator25519.generateKeys(seed)
        val keyPair2 = SecretGenerator25519.generateKeys(randomBytes(32))
        val ticket = consensus.generate(keyPair, puz).get
        val publicKey: PublicKey25519Proposition = keyPair.publicCommitment
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe true
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket), ticket, rootHash) shouldBe false
        consensus.validate(keyPair2.publicCommitment, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
        consensus.validate(publicKey, wrongBytes, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, wrongBytes) shouldBe false
      }
    }
  }


}