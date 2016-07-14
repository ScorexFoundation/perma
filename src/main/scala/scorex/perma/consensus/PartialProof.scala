package scorex.perma.consensus

import scorex.transaction.proof.Signature25519

case class PartialProof(signature: Signature25519, segmentIndex: Long, segment: PermaAuthData)
