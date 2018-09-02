package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.ops.Definitions.Settings
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, AbsOrder, Bot, Offer}
import me.mbcu.integrated.mmm.utils.MyLogging
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor

class OrderWsActor(bot: Bot, exchange: AbsExchange) extends AbsOrder(bot) with MyLogging{
  private implicit val ec: ExecutionContextExecutor = global

  var sortedSels: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var sortedBuys: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var logCancellable: Option[Cancellable] = None
  //  var state : Option[ActorRef] = None
  private var op: Option[ActorRef] = None

  var requestingTicker = false
  override def receive: Receive = {

    case "start" =>
      op = Some(sender())
      logCancellable = Some(context.system.scheduler.schedule(15 second, Settings.intervalLogSeconds seconds, self, message="log"))

    case "log" => info(Offer.dump(bot, sortedBuys, sortedSels))



  }

}
