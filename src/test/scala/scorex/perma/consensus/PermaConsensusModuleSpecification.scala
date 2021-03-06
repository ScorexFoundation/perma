package scorex.perma.consensus

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import scorex.Generators
import scorex.perma.application.TestAppSupport
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.SecretGenerator25519
import scorex.utils._

class PermaConsensusModuleSpecification extends PropSpec with PropertyChecks with GeneratorDrivenPropertyChecks
with Matchers with TestAppSupport with Generators {


  val consensus = app.consensusModule


  property("PartialProof serialization roundtrip") {
    forAll(proofsGen) { t: IndexedSeq[PartialProof] =>
      val decoded = PartialProof.parseBytes(t.head.bytes).get
      decoded.signature.signature shouldEqual t.head.signature.signature
    }
  }

  property("Ticket serialization roundtrip") {
    forAll(ticketGen) { t: Ticket =>
      val decoded = Ticket.parseBytes(t.bytes).get
    }
  }

  property("PermaConsensusBlockData serialization roundtrip") {
    forAll(blockData) { bd: PermaConsensusBlockData =>
      val decoded = PermaConsensusBlockData.parseBytes(bd.bytes).get
    }
  }

  property("generate/validate roundtrip") {
    val rootHash = TestAppSupport.rootHash
    forAll { (seed: Array[Byte], puz: Array[Byte], wrongBytes: Array[Byte]) =>
      whenever(seed.nonEmpty && puz.nonEmpty && wrongBytes.nonEmpty && !wrongBytes.sameElements(puz)) {
        val keyPair = SecretGenerator25519.generateKeys(seed)
        val keyPair2 = SecretGenerator25519.generateKeys(randomBytes(32))
        val ticket = consensus.generate(keyPair, puz).get
        val publicKey: PublicKey25519Proposition = keyPair.publicCommitment
//        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe true
//        consensus.validate(publicKey, puz, consensus.ticketScore(ticket), ticket, rootHash) shouldBe false
//        consensus.validate(keyPair2.publicCommitment, puz, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
//        consensus.validate(publicKey, wrongBytes, consensus.ticketScore(ticket) + 1, ticket, rootHash) shouldBe false
//        consensus.validate(publicKey, puz, consensus.ticketScore(ticket) + 1, ticket, wrongBytes) shouldBe false
      }
    }
  }

}