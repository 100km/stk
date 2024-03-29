package replicate

import play.api.libs.json.Json
import replicate.models.CheckpointData
import replicate.state.CheckpointsState
import replicate.utils.Types.RaceId
import replicate.utils.{Global, Infos}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

object RaceUtils {

  implicit val dispatcher: ExecutionContext = Global.dispatcher

  def loadRaceData: Iterator[CheckpointData] =
    Source.fromInputStream(getClass.getResourceAsStream("/dummy-timings.txt"), "utf-8").getLines().map(Json.parse(_).as[CheckpointData])

  def loadInfos: Infos = Json.parse(getClass.getResourceAsStream("/infos.json")).as[Infos]

  def installFullRace(pristine: Boolean = false): Future[Int] = {
    val infos = loadInfos
    CheckpointsState.reset()
    Future.sequence(for (checkpointData <- loadRaceData if RaceId.unwrap(checkpointData.raceId) > 0) yield {
      CheckpointsState.setTimes(if (pristine) checkpointData.pristine else checkpointData)
    }).map(_.size)
  }

}
