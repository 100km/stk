import akka.actor.{Actor, Cancellable}
import akka.util.Duration
import akka.util.duration._

trait PeriodicActor extends Actor {

  protected val period: Duration

  private var wakeupTimer: Option[Cancellable] = None

  private def startTimer() =
    wakeupTimer = wakeupTimer orElse Some(context.system.scheduler.schedule(0 seconds, period, self, 'wakeup))

  private def stopTimer() = {
    wakeupTimer.foreach (_.cancel())
    wakeupTimer = None
  }

  override def preStart() = {
    super.preStart()
    startTimer()
  }

  override def preRestart(cause: Throwable, msg: Option[Any]) = {
    stopTimer()
    super.preRestart(cause, msg)
  }

  override def postRestart(cause: Throwable) = {
    super.postRestart(cause)
    startTimer()
  }

  override def postStop() = {
    stopTimer()
    super.postStop()
  }

  override def receive = {
      case 'wakeup => periodic()
  }

  def periodic(): Unit

}