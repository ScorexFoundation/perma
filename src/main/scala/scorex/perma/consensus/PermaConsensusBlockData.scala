package scorex.perma.consensus

import scorex.block.ConsensusData
import scorex.settings.SizedConstants._
import scorex.transaction.box.proposition.PublicKey25519Proposition
import shapeless.Sized

case class PermaConsensusBlockData(parentId: Array[Byte],
                                   signature: Array[Byte],
                                   target: BigInt,
                                   puz: Sized[Array[Byte], Nat32],
                                   ticket: Ticket,
                                   producer: PublicKey25519Proposition) extends ConsensusData {
  override val BlockIdLength: Int = 64

  lazy val blockId = signature
}
