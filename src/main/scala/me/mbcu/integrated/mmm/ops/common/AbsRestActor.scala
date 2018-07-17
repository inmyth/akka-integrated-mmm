package me.mbcu.integrated.mmm.ops.common

import akka.actor.{Actor, ActorRef}
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._

object AbsRestActor {

  object As extends Enumeration {
    type As = Value
    val Seed, Trim, Counter, ClearOpenOrders, RoutineCheck = Value
  }

  trait SendRequest{
    def as:Option[As]
    def bot: Bot
    def book: ActorRef
  }

  case class GetOrderbook(page: Int, override val as:Option[As] = None)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class GetTicker(override val as:Option[As] = None)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class GetOwnPastTrades(override val as:Option[As] = None)(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class CancelOrder(id: String, as: Option[As])(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class NewOrder(offer: Offer, as: Option[As])(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class GetOrderInfo(id: String, override val as:Option[As])(implicit val bot:Bot, implicit val book:ActorRef) extends SendRequest

  case class GotStartPrice(price: Option[BigDecimal])

  case class GotOrderId(id: String, as: Option[As])

  case class GotOrderInfo(offer: Offer)

  case class GotOrderCancelled(id: String)

  case class GotOrderbook(offers: Seq[Offer], currentPage: Int, nextPage: Boolean)

  object StartRestActor

}

abstract class AbsRestActor() extends Actor {
  var op : Option[ActorRef] = None

  def sendRequest(r: SendRequest)

  def setOp(op: Option[ActorRef]) : Unit = this.op = op

  def start()

  def url: String

  def errorRetry(sendRequest: SendRequest, code: Int, msg: String, shouldEmail: Boolean = true): Unit = op foreach (_ ! ErrorRetryRest(sendRequest, code, msg, shouldEmail))

  def errorShutdown(shutdownCode: ShutdownCode, code: Int, msg: String): Unit = op foreach (_ ! ErrorShutdown(shutdownCode, code, msg))

  def errorIgnore(code: Int, msg: String): Unit = op foreach (_ ! ErrorIgnore(code, msg))

  override def receive: Receive = {

    case StartRestActor => start()

    case a: SendRequest => sendRequest(a)

  }

  def stringifyXWWWForm(params: Map[String, String]): String = params.map(r => s"${r._1}=${r._2}").mkString("&")

}
