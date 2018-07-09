package me.mbcu.integrated.mmm.ops.common

import akka.actor.{Actor, ActorRef}
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._

object AbsRestActor {

  trait SendRequest{
    def as:Option[String]
  }

  case class GetOrderbook(page: Int, override val as:Option[String] = None) extends SendRequest

  case class GetTicker(override val as:Option[String] = None) extends SendRequest

  case class GetOwnPastTrades(override val as:Option[String] = None) extends SendRequest

  case class CancelOrder(id: String, as: Option[String]) extends SendRequest

  case class NewOrder(offer: Offer, as: Option[String]) extends SendRequest

  case class GetOrderInfo(id: String, override val as:Option[String] = None) extends SendRequest

  case class GotStartPrice(price: Option[BigDecimal])

  case class GotOrderId(id: String)

  case class GotOrderInfo(offer: Offer)

  case class GotOrderCancelled(id: String)

  case class GotOrderbook(offers: Seq[Offer], currentPage: Int, nextPage: Boolean)

  object StartRestActor

}

abstract class AbsRestActor(bot: Bot) extends Actor {
  var op : Option[ActorRef] = None

  def sendRequest(r: SendRequest)

  def setOp(op: Option[ActorRef]) : Unit = this.op = op

  def start()

  def url: String

  def errorRetry(sendRequest: SendRequest, code: Int, msg: String): Unit = op foreach (_ ! ErrorRetryRest(sendRequest, code, msg))

  def errorShutdown(shutdownCode: ShutdownCode, code: Int, msg: String): Unit = op foreach (_ ! ErrorShutdown(shutdownCode, code, msg))

  def errorIgnore(code: Int, msg: String): Unit = op foreach (_ ! ErrorIgnore(code, msg))

  override def receive: Receive = {

    case StartRestActor => start()

    case a: SendRequest => sendRequest(a)

  }

  def stringifyXWWWForm(params: Map[String, String]): String = params.map(r => s"${r._1}=${r._2}").mkString("&")

}
