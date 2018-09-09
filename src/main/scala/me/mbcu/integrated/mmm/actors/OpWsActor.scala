package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OpWsActor._
import me.mbcu.integrated.mmm.actors.WsActor._
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsWsParser.{GotSubscribe, LoggedIn, SendWs}
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OpWsActor {

  case class QueueWs(sendWs: Seq[SendWs])

}

class OpWsActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends AbsOpActor(exchangeDef, bots, fileActor) with MyLogging {
  private var base: Option[ActorRef] = None
  private val wsEx: AbsWsExchange = exchangeDef.asInstanceOf[AbsWsExchange]
  private val parser: AbsWsParser = wsEx.getParser(self)
  private implicit val ec: ExecutionContextExecutor = global
  private var client: Option[ActorRef] = None
  private var botMap: Map[String, ActorRef] = Map.empty

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      botMap ++= bots.map(bot => bot.pair -> context.actorOf(Props(new OrderWsActor(bot, exchangeDef, wsEx.getRequest)), name = s"${bot.pair}"))
      botMap.values.foreach(_ ! "start")
      client = Some(context.actorOf(Props(new WsActor()), name = "wsclient"))
      client.foreach(_ ! "start")

    case a: WsDisconnected =>
      botMap.values.foreach(_ ! a)
      context.system.scheduler.scheduleOnce(a.reconnectMs seconds, self, "reconnect websocket")

    case "reconnect websocket" => client.foreach(_ ! WsConnect(exchangeDef.endpoint))

    case WsConnected => send(wsEx.getRequest.login(bots.head.credentials))

    case WsGotText(text) => parser.parse(text, botMap)

    case a: LoggedIn => send(wsEx.getRequest.subscribe)

    case a: GotSubscribe => botMap.values.foreach(_ ! a)

    case QueueWs(sendWs) => sendWs foreach send

    case a: ErrorShutdown => base foreach (_ ! a)

    case a: ErrorIgnore => base foreach (_ ! a)

  }

  def send(s: SendWs): Unit = {
    info(s"${s.as} : ${s.jsValue.toString}")
    client foreach(_ ! SendJs(s.jsValue))
  }



}
