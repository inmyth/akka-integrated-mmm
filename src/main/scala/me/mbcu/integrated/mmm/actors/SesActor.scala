package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import com.amazonaws.regions.Regions
import com.amazonaws.services.simpleemail.model.SendEmailResult
import jp.co.bizreach.ses.SESClient
import jp.co.bizreach.ses.models.{Address, Content, Email}
import me.mbcu.integrated.mmm.actors.SesActor.{CacheMessages, MailSent}
import me.mbcu.integrated.mmm.ops.Definitions
import me.mbcu.integrated.mmm.utils.MyLogging
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

object SesActor {

  case class CacheMessages(msg :String, shutdownCode : Option[Int])

  object MailTimer

  case class MailSent(t : Try[SendEmailResult], shutdownCode : Option[Int])

}

class SesActor(sesKey : Option[String], sesSecret : Option[String], emails : Option[Seq[String]]) extends Actor with MyLogging{
  private implicit val ec: ExecutionContextExecutor = global
  val title = "MMM Error"
  var tos : Seq[Address] = Seq.empty
  var cli : Option[SESClient] = None
  private implicit val region: Regions = Regions.US_EAST_1
  private var base : Option[ActorRef] = None
  private val cache = new ListBuffer[(String, Option[Int])]
  var sendCancellable : Option[Cancellable] = None

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      (sesKey, sesSecret, emails) match {
        case (Some(k), Some(s), Some(m)) =>
          cli = Some(SESClient(k, s))
          this.tos ++= m map (Address(_))
        case _ =>
      }

    case CacheMessages(msg, shutdownCode) =>
      sendCancellable foreach (_.cancel())
      sendCancellable = Some(context.system.scheduler.scheduleOnce(Definitions.Settings.cachingEmailSeconds second, self, "execute send"))
      cache += ((msg, shutdownCode))

    case "execute send" =>
      val shutdownCode = cache flatMap(_._2) reduceOption(_ min _)
      val msg = cache map (_._1) mkString "\n\n"
      send(title, msg, shutdownCode)
  }

  def send(title: String, body: String, shutdownCode : Option[Int] = None): Unit = {
    (tos.headOption, cli) match {
      case (Some(address), Some(c)) =>
        val email = Email(Content(title), address, Some(Content(body)), None, tos)
        val future = c send email
        future.onComplete(t => base foreach(_ ! MailSent(t, shutdownCode)))
      case _ =>
    }
  }
}
