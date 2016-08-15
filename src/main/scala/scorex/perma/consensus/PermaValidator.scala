package scorex.perma.consensus

import scorex.block.ConsensusValidator
import scorex.consensus.History
import scorex.utils.ScorexLogging

class PermaValidator(rootHash: Array[Byte]) extends ConsensusValidator[PermaConsensusBlockData]
with ScorexLogging {

  import PermaConsensusModule._

  override def isValid(c: PermaConsensusBlockData, history: History[_, _, _, PermaConsensusBlockData]): Boolean = {
    history.blockById(c.parentId) match {
      case Some(parent) =>
        val publicKey = c.producer
        val puz = generatePuz(parent.consensusData)
        val puzIsValid = c.puz.unsized sameElements puz.unsized
        val targetIsValid = c.target == calcTarget(parent.consensusData)(history)
        val ticketIsValid = validate(publicKey, c.puz.unsized, c.target, c.ticket, rootHash)
        puzIsValid && targetIsValid && ticketIsValid
      case None => false
    }
  }

}