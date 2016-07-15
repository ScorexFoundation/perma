package scorex

import org.scalacheck.{Arbitrary, Gen}
import scorex.crypto.authds.merkle.MerklePath
import scorex.crypto.encode.Base58
import scorex.perma.consensus.{PartialProof, PermaAuthData, PermaConsensusBlockData, Ticket}
import scorex.settings.SizedConstants._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.proof.Signature25519
import scorex.utils.randomBytes
import shapeless.Sized

trait Generators {

  val bytes = Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte]).map(_.toArray)
  val sizedBytes: Gen[Sized[Array[Byte], Nat32]] = Gen.listOfN(32, Arbitrary.arbitrary[Byte]).map(a => Sized.wrap(a.toArray))

  val segmentGen: Gen[PermaAuthData] = for {
    data <- bytes
    index <- Arbitrary.arbitrary[Long]
    hashes <- Gen.nonEmptyListOf(bytes)
  } yield new PermaAuthData(data, MerklePath(index, hashes))

  val proofGen: Gen[PartialProof] = for {
    signature: Signature25519 <- bytes.map(Signature25519(_))
    segmentIndex: Long <- Arbitrary.arbitrary[Long]
    segment: PermaAuthData <- segmentGen

  } yield PartialProof(signature, segmentIndex: Long, segment: PermaAuthData)

  val proofsGen: Gen[IndexedSeq[PartialProof]] = Gen.nonEmptyListOf(proofGen).map(_.toIndexedSeq)

  val ticketGen: Gen[Ticket] = for {
    publicKey: Array[Byte] <- bytes
    s: Array[Byte] <- bytes
    proofs: IndexedSeq[PartialProof] <- proofsGen
  } yield Ticket(publicKey, s, proofs)

  val gen = PublicKey25519Proposition(Sized.wrap(Array.fill(32)(0: Byte)))

  val blockData = for {
    parentId: Array[Byte] <- bytes
    signature: Array[Byte] <- bytes
    target: BigInt <- Arbitrary.arbitrary[BigInt]
    puz: Sized[Array[Byte], Nat32] <- sizedBytes
    pubk: Sized[Array[Byte], Nat32] <- sizedBytes
    ticket: Ticket <- ticketGen
  } yield {
      PermaConsensusBlockData(parentId, signature, target, puz, ticket, gen)
    }

}
