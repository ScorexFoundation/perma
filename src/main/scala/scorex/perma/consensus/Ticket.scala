package scorex.perma.consensus

case class Ticket(publicKey: Array[Byte],
                  s: Array[Byte],
                  proofs: IndexedSeq[PartialProof])


