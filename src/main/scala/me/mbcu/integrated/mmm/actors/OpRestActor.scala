package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderbookRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.internal.Definitions

object OpRestActor {

}

class OpRestActor(exchangeDef: AbsExchange, bots: Seq[Bot]) extends Actor with MyLogging {
  private implicit val ec: ExecutionContextExecutor = global
  private var base: Option[ActorRef] = None
  private var rest: Option[ActorRef] = None
//  private var book: Option[ActorRef] = None
  private var dqCancellable: Option[Cancellable] = None
  private val q = new mutable.Queue[SendRequest]

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      rest = Some(context.actorOf(exchangeDef.getActorRefProps))
      rest foreach (_ ! StartRestActor)
//      book = Some(context.actorOf(Props(new OrderbookRestActor(bot))))
//      book foreach (_ ! "start")
      bots.foreach(bot => {
        val book = context.actorOf(Props(new OrderbookRestActor(bot)))
        book ! "start"
      })
      self ! "init dequeue scheduler"

    case "init dequeue scheduler" => dqCancellable = Some(context.system.scheduler.schedule(1 second, exchangeDef.intervalMillis milliseconds, self, "dequeue"))

    case "dequeue" => if (q.nonEmpty) {
      val next = q.dequeue()
      info(s"${exchangeDef.name} / ${next.bot.pair} : ${next.as}")
      rest foreach(_ ! next)
    }

    case QueueRequest(seq) => q ++= seq

//    case a: GotOrderbook => book foreach (_ ! a)

//    case a: GotOrderCancelled => book foreach (_ ! a)

//    case GotStartPrice(price) => price match {
//      case Some(p) => book.foreach(_ ! GotStartPrice(price))
//      case _ =>
//        error(s"OpRest#GotStartPrice : Starting price for ${bot.exchange} / ${bot.pair} not found. Try different startPrice in bot")
//        System.exit(-1)
//    }

//    case GotOrderId(id) => q += GetOrderInfo(id)

//    case a : GotOrderInfo => book foreach(_ ! a)

    case ErrorRetryRest(sendRequest, code, msg) =>
      base foreach(_ ! ErrorRetryRest(sendRequest, code, msg))
      q += sendRequest

    case a : ErrorShutdown => base foreach(_ ! a)

    case a : ErrorIgnore => base foreach(_ ! a)

  }

}
