package replicate

import org.specs2.mutable._
import org.specs2.specification.Scope
import replicate.models.CheckpointData
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.Point
import replicate.utils.Global
import replicate.utils.Types._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

class CheckpointsStateSpec extends Specification {

  sequential

  trait CleanRanking extends Scope with BeforeAfter {
    implicit val dispatcher: ExecutionContext = Global.dispatcher
    def before = Await.ready(CheckpointsState.reset(), 1.second)
    def after = Await.ready(CheckpointsState.reset(), 1.second)
  }

  "checkpointData#pristine()" should {

    "insert deleted timestamps" in {
      CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1300), Vector.empty, Vector.empty).pristine should be equalTo CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1300), Vector.empty, Vector.empty)
      CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1300), Vector(1000, 1100), Vector.empty).pristine should be equalTo CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1000, 1100, 1300), Vector.empty, Vector.empty)
    }

    "remove artificial timestamps" in {
      CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1000, 1100), Vector.empty, Vector.empty).pristine should be equalTo CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1000, 1100), Vector.empty, Vector.empty)
      CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1000, 1100), Vector.empty, Vector(950, 1100)).pristine should be equalTo CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(1000), Vector.empty, Vector.empty)
    }
  }

  "setTimes()" should {

    "return a sorted list of timestamps" in new CleanRanking {
      val result1 = Await.result(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950, 1300), Vector.empty, Vector.empty)), 1.second)
      CheckpointsState.sortedTimestamps(result1) must be equalTo Vector(Point(SiteId(1), 950), Point(SiteId(1), 1300))
      val result2 = Await.result(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(2), Vector(800, 900, 1000, 1100), Vector.empty, Vector.empty)), 1.second)
      CheckpointsState.sortedTimestamps(result2) must be equalTo Vector(Point(SiteId(2), 800), Point(SiteId(2), 900), Point(SiteId(1), 950), Point(SiteId(2), 1000), Point(SiteId(2), 1100), Point(SiteId(1), 1300))
    }
  }

  "timesFor()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(CheckpointsState.timesFor(RaceId(1), ContestantId(42)), 1.second) must beEmpty
    }

    "return the existing points for a contestant" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(2), Vector(1000, 900, 1100, 800), Vector.empty, Vector.empty)), 1.second)
      Await.result(CheckpointsState.timesFor(RaceId(1), ContestantId(42)), 1.second) must be equalTo Vector(Point(SiteId(2), 800), Point(SiteId(2), 900), Point(SiteId(1), 950), Point(SiteId(2), 1000), Point(SiteId(2), 1100))
    }
  }

  "checkpointDataFor()" should {

    "acknowledge the absence of information about a contestant" in new CleanRanking {
      Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(42)), 1.second) must beEmpty
    }

    "return the existing points for a contestant" in new CleanRanking {
      private val cpd1: CheckpointData = CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)
      private val cpd2: CheckpointData = CheckpointData(RaceId(1), ContestantId(42), SiteId(2), Vector(1000, 900, 1100, 800), Vector.empty, Vector.empty)
      Await.ready(CheckpointsState.setTimes(cpd1), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd2), 1.second)
      Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(42)), 1.second) must be equalTo Vector(cpd1, cpd2)
    }

    "replace data for a given site id" in new CleanRanking {
      private val cpd1: CheckpointData = CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)
      private val cpd2: CheckpointData = CheckpointData(RaceId(1), ContestantId(42), SiteId(2), Vector(1000, 900, 1100, 800), Vector.empty, Vector.empty)
      private val cpd1bis: CheckpointData = CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)
      Await.ready(CheckpointsState.setTimes(cpd1), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd2), 1.second)
      Await.ready(CheckpointsState.setTimes(cpd1bis), 1.second)
      Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(42)), 1.second) must be equalTo Vector(cpd2, cpd1bis)
    }
  }

  "contestants()" should {

    "return the full list of contestants" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(50), SiteId(2), Vector(1000, 900, 1100, 800), Vector.empty, Vector.empty)), 1.second)
      Await.result(CheckpointsState.contestants(RaceId(1)), 1.second) must be equalTo Set(ContestantId(42), ContestantId(50))
    }

    "remove contestants with no points" in new CleanRanking {
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(950), Vector.empty, Vector.empty)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(42), SiteId(1), Vector(), Vector(950), Vector.empty)), 1.second)
      Await.ready(CheckpointsState.setTimes(CheckpointData(RaceId(1), ContestantId(50), SiteId(2), Vector(1000, 900, 1100, 800), Vector.empty, Vector.empty)), 1.second)
      Await.result(CheckpointsState.contestants(RaceId(1)), 1.second) must be equalTo Set(ContestantId(50))
    }
  }

  "a full simulation" should {

    "load a full race information in a reasonable time" in {
      Await.result(RaceUtils.installFullRace(), 5.seconds) must be equalTo 5193
    }

    "restore the saved checkpoints" in {
      val result = Await.result(CheckpointsState.checkpointDataFor(RaceId(1), ContestantId(59)), 1.second)
      result must have size 7
      result.foreach(_.timestamps must have size 3)
      result.map(_.deletedTimestamps.size).sum must be equalTo 1
      result.map(_.insertedTimestamps.size).sum must be equalTo 2
    }

  }

}

