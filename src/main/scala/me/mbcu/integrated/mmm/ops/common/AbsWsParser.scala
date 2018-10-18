package me.mbcu.integrated.mmm.ops.common

import akka.actor.ActorRef
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorShutdown}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsWsParser.SendWs
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.scala.MyLogging
import play.api.libs.json.JsValue

trait AbsWsRequest {

  def subscribe: SendWs

  def cancelOrder(orderId: String, as: As): SendWs

  def newOrder(offer: Offer, as: As): SendWs

  def login(credentials: Credentials): SendWs

  def subsTicker(pair: String): SendWs

  def unsubsTicker(pair: String): SendWs

}


object AbsWsParser {

  trait GotWs {
    def requestId: String
  }


  case class SendWs(requestId: String, jsValue: JsValue, as: As)
  
  case class RemoveOfferWs(isRetry: Boolean, orderId: String, side: Side,  override val requestId: String) extends GotWs

  case class GotActiveOrdersWs(offers: Seq[Offer], override val requestId: String) extends GotWs

  case class GotOfferWs(offer: Offer, override val requestId: String) extends GotWs

  case class GotTickerPriceWs(price: Option[BigDecimal], override val requestId: String) extends GotWs

  case class RemovePendingWs(override val requestId: String) extends GotWs

  case class RetryPendingWs(override val requestId: String) extends GotWs

  case class GotSubscribe(override val requestId: String) extends GotWs

  case class LoggedIn(override val requestId: String) extends GotWs
}

abstract class AbsWsParser(op: ActorRef) extends MyLogging{

  def parse(raw: String, botMap: Map[String, ActorRef]): Unit

  def errorShutdown(shutdownCode: ShutdownCode, code: Int, msg: String): Unit = op ! ErrorShutdown(shutdownCode, code, msg)

  def errorIgnore(code: Int, msg: String, shouldEmail: Boolean = false): Unit = op  ! ErrorIgnore(code, msg, shouldEmail)
}

