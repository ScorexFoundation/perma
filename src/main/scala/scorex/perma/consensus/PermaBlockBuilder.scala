package scorex.perma.consensus

import scorex.block.{Block, TransactionalData}
import scorex.settings.SizedConstants._
import scorex.transaction.Transaction
import scorex.transaction.box.proposition.PublicKey25519Proposition
import scorex.transaction.state.PrivateKey25519Holder
import shapeless.Sized

object PermaBlockBuilder {
  
  def buildAndSign[TX <: Transaction[PublicKey25519Proposition, TX],
  TData <: TransactionalData[TX]](transData: TData,
                                  version: Byte,
                                  timestamp: Long,
                                  parentId: Array[Byte],
                                  target: BigInt,
                                  puz: Sized[Array[Byte], Nat32],
                                  ticket: Ticket,
                                  producer: PrivateKey25519Holder):
  Block[PublicKey25519Proposition, TData, PermaConsensusBlockData] = {
    val consData = PermaConsensusBlockData(parentId, Array(), target, puz, ticket, producer.publicCommitment)
    val toSign = new Block[PublicKey25519Proposition, TData, PermaConsensusBlockData](version, timestamp, consData, transData)
    val sig = producer.sign(toSign.bytes).proofBytes
    new Block(version, timestamp, consData.copy(signature = sig), transData)
  }

}
