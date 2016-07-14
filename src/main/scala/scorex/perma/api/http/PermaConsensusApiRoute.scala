package scorex.perma.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import io.swagger.annotations._
import scorex.api.http.{ApiRoute, CommonApiFunctions}
import scorex.crypto.encode.Base58
import scorex.perma.consensus.PermaConsensusModule
import scorex.settings.Settings

@Path("/consensus")
@Api(value = "/consensus", description = "Consensus-related calls")
class PermaConsensusApiRoute[TX, TD](consensusModule: PermaConsensusModule[TX, TD], override val settings: Settings)
                                    (implicit val context: ActorRefFactory)
  extends ApiRoute with CommonApiFunctions {

  override val route: Route =
    pathPrefix("consensus") {
      algo ~ target ~ targetId ~ puz ~ puzId
    }

  @Path("/target")
  @ApiOperation(value = "Last target", notes = "Target of a last block", httpMethod = "GET")
  def target: Route = {
    path("target") {
      getJsonRoute {
        ("target" -> consensusModule.lastBlock.consensusData.target).asJson
      }
    }
  }

  @Path("/target/{blockId}")
  @ApiOperation(value = "Target of selected block", notes = "Target of a block with specified id", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "Block id ", required = true, dataType = "String", paramType = "path")
  ))
  def targetId: Route = {
    path("target" / Segment) { case encodedSignature =>
      getJsonRoute {
        withBlock(consensusModule, encodedSignature) { block =>
          ("target" -> block.consensusData.target).asJson
        }
      }
    }
  }

  @Path("/puz")
  @ApiOperation(value = "Current puzzle", notes = "Current puzzle", httpMethod = "GET")
  def puz: Route = {
    path("puz") {
      getJsonRoute {
        ("puz" -> Base58.encode(consensusModule.lastBlock.consensusData.puz)).asJson
      }
    }
  }

  @Path("/puz/{blockId}")
  @ApiOperation(value = "Puzzle of selected block", notes = "Puzzle of a block with specified id", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "Block id ", required = true, dataType = "String", paramType = "path")
  ))
  def puzId: Route = {
    path("puz" / Segment) { case encodedSignature =>
      getJsonRoute {
        withBlock(consensusModule, encodedSignature) { block =>
          ("baseTarget" -> Base58.encode(block.consensusData.puz)).asJson
        }
      }
    }
  }

  @Path("/algo")
  @ApiOperation(value = "Consensus algo", notes = "Shows which consensus algo being using", httpMethod = "GET")
  def algo: Route = {
    path("algo") {
      getJsonRoute {
        ("consensusAlgo" -> "nxt").asJson
      }
    }
  }
}
