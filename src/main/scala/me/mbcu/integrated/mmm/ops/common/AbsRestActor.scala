package me.mbcu.integrated.mmm.ops.common

import akka.actor.{Actor, ActorRef}
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.StartMethods.StartMethods

object AbsRestActor {

  object As extends Enumeration {
    type As = Value
    val Seed, Trim, Counter, ClearOpenOrders, RoutineCheck, Init = Value
  }

  trait SendRest{
    def as:As
    def bot: Bot
    def book: ActorRef
  }

  trait GotRest{
    def arriveMs : Long
    def send : SendRest
  }

  case class GetActiveOrders(lastMs: Long, cache: Seq[Offer], page: Int, override val as: As)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRest

  case class GetFilledOrders(lastCounterId: String, override val as: As)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRest

  case class CancelOrder(id: String, as: As)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRest

  case class NewOrder(offer: Offer, as: As)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRest

  case class GetTickerStartPrice(override val as:As)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRest

  case class GotNewOrder(override val arriveMs: Long, override val send: NewOrder) extends GotRest

  case class GotTickerStartPrice(price: Option[BigDecimal], override val arriveMs: Long, override val send: GetTickerStartPrice) extends GotRest

  case class GotOrderCancelled(id: String, as: As, override val arriveMs: Long, override val send: CancelOrder) extends GotRest

  case class GotActiveOrders(offers: Seq[Offer], currentPage: Int, nextPage: Boolean, override val arriveMs: Long, override val send: GetActiveOrders) extends GotRest

  case class GotUncounteredOrders(offers: Seq[Offer], latestCounterId: Option[String], isSortedFromOldest: Boolean = true, override val arriveMs: Long, override val send: GetFilledOrders) extends GotRest

  object StartRestActor

}

abstract class AbsRestActor() extends Actor {
  var op : Option[ActorRef] = None

  def sendRequest(r: SendRest)

  def setOp(op: Option[ActorRef]) : Unit = this.op = op

  def start()

  def url: String

  def errorRetry(sendRequest: SendRest, code: Int, msg: String, shouldEmail: Boolean = true): Unit = op foreach (_ ! ErrorRetryRest(sendRequest, code, msg, shouldEmail))

  def errorShutdown(shutdownCode: ShutdownCode, code: Int, msg: String): Unit = op foreach (_ ! ErrorShutdown(shutdownCode, code, msg))

  def errorIgnore(code: Int, msg: String, shouldEmail: Boolean = false): Unit = op foreach (_ ! ErrorIgnore(code, msg, shouldEmail))

  override def receive: Receive = {

    case StartRestActor => start()

    case a: SendRest => sendRequest(a)

  }

  def stringifyXWWWForm(params: Map[String, String]): String = params.map(r => s"${r._1}=${r._2}").mkString("&")

}
