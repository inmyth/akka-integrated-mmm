package me.mbcu.integrated.mmm.ops.common

import akka.actor.Actor
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._

object AbsRestActor {

  object StartRestActor

  class SendRequest

  case class GetOrderbook(page: Int) extends SendRequest

  case class GetTicker() extends SendRequest

  case class GetOwnPastTrades() extends SendRequest

  case class CancelOrder(id : String) extends SendRequest

  case class NewOrder(offer: Offer) extends SendRequest

  case class GetOrderInfo(id :String) extends SendRequest

  case class GotStartPrice(price : Option[BigDecimal])

  case class GotOrderId(id : String)

  case class GotOrderInfo(offer : Offer)

  case class GotOrderCancelled(id: String)

  case class GotOrderbook(offers : Seq[Offer], currentPage : Int, nextPage : Boolean)
}

abstract class AbsRestActor(bot: Bot) extends Actor  {

  def sendRequest(r : SendRequest)

  def start()

  def url: String

  def errorShutdown(shutdownCode : ShutdownCode, code : Int, msg : String) : Unit

  def errorIgnore(code : Int, msg : String) : Unit

  def errorRetry(sendRequest: SendRequest, code : Int, msg : String) : Unit

  override def receive: Receive = {

    case StartRestActor => start()

    case a : SendRequest => sendRequest(a)

  }

  def stringifyXWWWForm(params : Map[String, String]) : String = params.map(r => s"${r._1}=${r._2}").mkString("&")

}
