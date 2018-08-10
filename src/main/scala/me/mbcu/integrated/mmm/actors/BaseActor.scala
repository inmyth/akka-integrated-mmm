package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.BaseActor.{ConfigReady, Shutdown}
import me.mbcu.integrated.mmm.actors.OrderRestActor.{GetLastCounter, WriteLastCounter}
import me.mbcu.integrated.mmm.actors.SesActor.{CacheMessages, MailSent}
import me.mbcu.integrated.mmm.ops.Definitions
import me.mbcu.integrated.mmm.ops.Definitions._
import me.mbcu.integrated.mmm.ops.common.{Bot, Config}
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object BaseActor {

  case class ConfigReady(config: Option[Config])

  case class HandleRPCError(shutdowncode: Option[Int], errorCode: Int, msg: String)

  case class Shutdown(code: Option[Int])

  case class HandleError(msg: String, code: Option[Int] = None)

  case class ConfigUpdate(bot:Bot, lastGetFilledMs: Long )

}

class BaseActor(configPath: String, msPath: String) extends Actor with MyLogging {
  private var config: Option[Config] = None
  private var ses: Option[ActorRef] = None
  private implicit val ec: ExecutionContextExecutor = global
  val botName = "bot-%s"
  private var fileActor: Option[ActorRef] = None
  
  def receive: Receive = {

    case "start" =>
      fileActor = Some(context.actorOf(Props(new FileActor(configPath, msPath)), name ="fio"))
      fileActor foreach(_ ! "start")

    case ConfigReady(tryCfg) => tryCfg match {

      case Some(c) =>
        config = Some(c)
        ses = Some(context.actorOf(Props(new SesActor(c.env.sesKey, c.env.sesSecret, c.env.emails)), name = "ses"))
        ses foreach (_ ! "start")
        self ! "init bot"

      case _ => self ! Shutdown(Some(ShutdownCode.fatal.id))
    }

    case "init bot" =>
      (config, fileActor) match {
        case (Some(c), Some(f)) =>
           Config.groupBots(c.bots).zipWithIndex.foreach {
            case (b, i) => val excDef = Definitions.exchangeMap(b._1)
              val props = excDef.protocol match {
                  case Op.rest => Props(new OpRestActor(excDef, b._2, f))
                  case Op.ddex => Props(new OpDdexActor(excDef, b._2, f))
                  case Op.ws => Props(new OpWsActor(excDef, b._2, f))
                  case Op.restgi => Props(new OpGIActor(excDef, b._2))
                }
              val opActor = context.actorOf(props, botName.format(i))
              opActor ! "start"
          }
        case _ =>
      }

    case ErrorShutdown(shutdown, code, msg) => ses foreach(_ ! CacheMessages(msg, Some(shutdown.id)))

    case ErrorIgnore(code, msg, shouldEmail) => if(shouldEmail) ses foreach(_ ! CacheMessages(msg, None))

    case ErrorRetryRest(sendRequest, code, msg, shouldEmail) => if(shouldEmail) ses foreach(_ ! CacheMessages(msg, None))

    case MailSent(t, shutdownCode) =>
      t match {
        case Success(_) => info("Email Sent")
        case Failure(c) => info(
          s"""Failed sending email
             |${c.getMessage}
              """.stripMargin)
      }
      self ! Shutdown(shutdownCode)

    case Shutdown(code) =>
      code foreach (_ => {
        info(s"Stopping application, shutdown code $code")
        implicit val executionContext: ExecutionContext = context.system.dispatcher
        context.system.scheduler.scheduleOnce(Duration.Zero)(System.exit(_))
      })

  }

}
