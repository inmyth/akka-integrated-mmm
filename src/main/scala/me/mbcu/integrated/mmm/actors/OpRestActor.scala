package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderbookRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OpRestActor {

}

class OpRestActor(exchangeDef: AbsExchange, bots: Seq[Bot]) extends Actor with MyLogging {
  private implicit val ec: ExecutionContextExecutor = global
  private var base: Option[ActorRef] = None
  private var rest: Option[ActorRef] = None
  private var dqCancellable: Option[Cancellable] = None
  private val q = new scala.collection.mutable.Queue[SendRequest]

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      rest = Some(context.actorOf(exchangeDef.getActorRefProps))
      rest foreach (_ ! StartRestActor)
      bots.foreach(bot => {
        val book = context.actorOf(Props(new OrderbookRestActor(bot)), name= s"${bot.pair}")
        book ! "start"
      })
      self ! "init dequeue scheduler"

    case "init dequeue scheduler" => dqCancellable = Some(context.system.scheduler.schedule(1 second, exchangeDef.intervalMillis milliseconds, self, "dequeue"))

    case "dequeue" => if (q.nonEmpty) {
      val next = q.dequeue()
      rest foreach(_ ! next)
    }

    case QueueRequest(seq) => q ++= seq

    case a : CheckInQueue => if (isNotInQueue(a.bot, a.ass)) a.book ! a.msg

    case a : QueueGetOrderInfo => q ++= removeAlreadyInQueue(a.bot, a.batch)

    case ErrorRetryRest(sendRequest, code, msg, shouldEmail) =>
      base foreach(_ ! ErrorRetryRest(sendRequest, code, msg, shouldEmail))
      q += sendRequest

    case a : ErrorShutdown => base foreach(_ ! a)

    case a : ErrorIgnore => base foreach(_ ! a)

  }

  def isNotInQueue(bot: Bot, ass: Seq[As]): Boolean = {
    val qs = q.filter(_.bot.exchange == bot.exchange).filter(_.bot.pair == bot.pair).flatMap(_.as).toList
    ass.map(qs.contains(_)).forall(_ == false)
  }

  def removeAlreadyInQueue(bot: Bot, batch: Seq[GetOrderInfo]): Seq[GetOrderInfo] = {
    val prep = q.filter(_.bot.exchange == bot.exchange).filter(_.bot.pair == bot.pair).collect {
      case a: GetOrderInfo => a
    }.map(_.id)
    batch.filter(a => !prep.contains(a.id))
  }

}
