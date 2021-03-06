package replicate.maintenance

import net.rfc1149.canape._
import net.rfc1149.canape.helpers._
import org.slf4j.Logger
import play.api.libs.json.Reads._
import play.api.libs.json._
import replicate.models.CheckpointData
import replicate.utils.Global._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ConflictsSolver {

  val log: Logger

  private def solveConflicts(db: Database, id: String, revs: Vector[String]): Future[Seq[JsObject]] =
    getRevs(db, id, revs) flatMap {
      docs =>
        val f = solve(db, docs)(makeSolver[CheckpointData](_.reduce(_.merge(_))))
        f.onComplete {
          case Success(result) => log.info("solved conflicts for {} ({} documents)", id, revs.size)
          case Failure(t)      => log.error("unable to solve conflicts for {} ({} documents)", id, revs.size, t)
        }
        f
    }

  /**
   * Some checkpoints might be modified at several places at the same time. In this case, merging
   * is required.
   *
   * @param db the database
   * @return a list of checkpoints whose conflicts have been resolved
   */
  def fixConflictingCheckpoints(db: Database): Future[Iterable[Seq[JsObject]]] =
    db.mapOnly("common", "conflicting-checkpoints") flatMap { r =>
      Future.sequence(for ((id, _, value) <- r.items[JsValue, Vector[String]])
        yield solveConflicts(db, id, value))
    }

}
