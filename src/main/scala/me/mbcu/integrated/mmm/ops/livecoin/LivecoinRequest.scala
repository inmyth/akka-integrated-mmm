package me.mbcu.integrated.mmm.ops.livecoin

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRequest, Credentials, Side}
import me.mbcu.integrated.mmm.ops.livecoin.LivecoinRequest.LivecoinState.LivecoinState
import play.api.libs.json.{Json, Reads, Writes}

object LivecoinRequest extends AbsRequest {

  object LivecoinState extends Enumeration {
    type LivecoinState = Value
    val ALL, OPEN, CLOSED, CANCELLED, EXECUTED, NOT_CANCELLED, PARTIALLY, PARTIALLY_FILLED, PARTIALLY_FILLED_AND_CANCELLED = Value

    implicit val read = Reads.enumNameReads(LivecoinState)
    implicit val write = Writes.enumNameWrites
  }

  case class LivecoinParams(sign: String, url: String = "", params: String)

  def getTicker(pair: String): String = Livecoin.endpoint.format(s"exchange/ticker?currencyPair=${urlEncode(pair)}")


  def getOrders(credentials: Credentials, pair: String, state: LivecoinState, after: Option[Long]): LivecoinParams =
    getOrders(credentials.signature, pair, state, after)

  def getOrders(secret: String, pair: String, state: LivecoinState, after: Option[Long]): LivecoinParams = {
    var params = after match {
      case Some(l) => Map(
        "currencyPair" -> pair,
        "openClosed" -> state.toString,
        "issuedFrom" -> l.toString
      )
      case _ => Map(
        "currencyPair" -> pair,
        "openClosed" -> state.toString
      )
    }
    if (after.isDefined) params += ("issuedFrom" -> after.head.toString)
    val sorted = sortToForm(params)
    LivecoinParams(sign(sorted, secret), Livecoin.endpoint.format(s"exchange/client_orders?$sorted"),  sorted)
  }

  def newOrder(credentials: Credentials, pair: String, side: Side, price: BigDecimal, amount: BigDecimal): LivecoinParams = newOrder(credentials.signature, pair, side, price, amount)

  def newOrder(secret: String, pair:String, side: Side, price: BigDecimal, amount: BigDecimal): LivecoinParams = {
    val params = Map(
      "currencyPair" -> pair,
      "price" -> price.bigDecimal.toPlainString,
      "quantity" -> amount.bigDecimal.toPlainString
    )
    val sorted = sortToForm(params)
    val path = if (side == Side.buy) s"exchange/buylimit" else s"exchange/selllimit"
    LivecoinParams(sign(sorted, secret), Livecoin.endpoint.format(path),  sorted)
  }

  def cancelOrder(credentials: Credentials, pair: String, orderId: String): LivecoinParams = cancelOrder(credentials.signature, pair, orderId)

  def cancelOrder(secret: String, pair: String, orderId: String): LivecoinParams = {
    val params = Map(
      "currencyPair" -> pair,
      "orderId" -> orderId
    )
    val sorted = sortToForm(params)
    LivecoinParams(sign(sorted, secret), Livecoin.endpoint.format("exchange/cancellimit"), sorted)
  }

  def getOrderInfo(credentials: Credentials, orderId: String): LivecoinParams = getOrderInfo(credentials.signature, orderId)

  def getOrderInfo(secret: String, orderId: String): LivecoinParams = {
    val params = Map(
      "orderId" -> orderId
    )
    val sorted = sortToForm(params)
    LivecoinParams(sign(sorted, secret), Livecoin.endpoint.format(s"/exchange/order?$sorted"), sorted)
  }



  def sign(sorted:String, secret: String): String = toHex(signHmacSHA256(secret, sorted))

}

