package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, AbsOpActor, Bot}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

class OpWsActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends AbsOpActor(exchangeDef, bots, fileActor) {
  private implicit val ec: ExecutionContextExecutor = global
  private var ws: Option[ActorRef] = None
  private var base: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      ws = Some(context.actorOf(Props(new WsActor()), name = "wsclient"))
      ws.foreach(_ ! "start")
      bots.map(bot => context.actorOf(Props(new OrderWsActor(bot, exchangeDef)), name = s"${bot.pair}")).foreach(_ ! "start")


  }
}
