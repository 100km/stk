import akka.actor.ActorSystem
import net.rfc1149.octopush._
import scala.concurrent.Await;
import scala.concurrent.duration._;
import scala.util.{Failure, Success}

import Octopush._

object SMS extends App {

  case class Config(userLogin: String = "", apiKey: String = "", sender: String = "stk100km", recipient: String = "", message: String = "This is a test message")

  val parser = new scopt.OptionParser[Config]("sms") {
    arg[String]("user-login") action { (x, c) => c.copy(userLogin = x) } text "API login"
    arg[String]("api-key") action { (x, c) => c.copy(apiKey = x) } text "API key"
    arg[String]("recipient") action { (x, c) =>
      c.copy(recipient = x)
    } text "Phone number to send the SMS to"
    opt[String]('m', "message") action { (x, c) =>
      c.copy(message = x)
    } text "Text message (default: This is a test message)"
    opt[String]('s', "sender") action { (x, c) =>
      c.copy(sender = x)
    } text "Sender (default: stk100km)"
    help("help") abbr "h" text "show this help"
    override val showUsageOnError = Some(true)
  }
  val options = parser.parse(args, Config()) getOrElse { sys.exit(1) }

  implicit val actorSystem: ActorSystem = ActorSystem()
  val octopush = new Octopush(options.userLogin, options.apiKey)
  val sms = Octopush.SMS(smsRecipients = List(options.recipient), smsText = s"${options.message} - STOP au XXXXX", smsType = PremiumFrance, smsSender = Some(options.sender))
  println(s"""Sending "${options.message}" to ${options.recipient}""")
  try {
    val result = Await.result(octopush.sms(sms), 10.seconds)
    println(f"""SMS status:
  - cost: ${result.cost}%.02f${result.currencyCode}
  - balance: ${result.balance}%.02f${result.currencyCode}
  - ticket: ${result.ticket}
  - sending date: ${result.sendingDate}
  - number of sendings: ${result.numberOfSendings}
  - number of send success: ${result.successes.size}
  - successes:""")
    for (s <- result.successes) {
      print(f"""    - recipient: ${s.recipient}
      country code: ${s.countryCode}
 """)
    }
  } catch {
    case t: Throwable => println(s"Could not send SMS: ${t.getLocalizedMessage}")
  }
  actorSystem.terminate()
}
