package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderRestActor.{CheckSafeForSeed, GetLastCounter, SafeForSeed, WriteLastCounter}
import me.mbcu.integrated.mmm.actors.OrderbookRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OpRestActor extends MyLogging {

  def isSafeForSeed(q: mutable.Queue[SendRest], nos: mutable.Set[NewOrder], bot: Bot): Boolean =
    (q.filter(_.bot == bot) ++ nos.filter(_.bot == bot).toSeq)
    .collect {
      case a: NewOrder => a
      case a: CancelOrder => a
    }
    .isEmpty

}

class OpRestActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends Actor with MyLogging {
  private implicit val ec: ExecutionContextExecutor = global
  private var base: Option[ActorRef] = None
  private var rest: Option[ActorRef] = None
  private var dqCancellable: Option[Cancellable] = None
  private val q = new scala.collection.mutable.Queue[SendRest]
  private val nos = scala.collection.mutable.Set[NewOrder]()

  override def receive: Receive = {

    case "start" =>
      base = Some(sender)
      rest = Some(context.actorOf(exchangeDef.getActorRefProps))
      rest foreach (_ ! StartRestActor)
      bots.foreach(bot => {
        val book = context.actorOf(Props(new OrderRestActor(bot, exchangeDef, fileActor)), name= s"${bot.pair}")
        book ! "start"
      })
      self ! "init dequeue scheduler"

    case "init dequeue scheduler" => dqCancellable = Some(context.system.scheduler.schedule(1 second, exchangeDef.intervalMillis milliseconds, self, "dequeue"))

    case "dequeue" =>
      if (q.nonEmpty) {
        val next = q.dequeue()
        next match {
          case order: NewOrder => nos += order
          case _ =>
        }
        rest foreach(_ ! next)
      }

    case QueueRequest(seq) => q ++= seq

    case a: GotNewOrderId => nos -= a.send

    case CheckSafeForSeed(ref, bot) => ref ! SafeForSeed(OpRestActor.isSafeForSeed(q, nos, bot))

    case ErrorRetryRest(sendRequest, code, msg, shouldEmail) =>
      base foreach(_ ! ErrorRetryRest(sendRequest, code, msg, shouldEmail))
      q += sendRequest

    case a : ErrorShutdown => base foreach(_ ! a)

    case a : ErrorIgnore => base foreach(_ ! a)



  }


}
