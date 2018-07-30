package me.mbcu.integrated.mmm.ops.yobit

import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRequest, Credentials}

object YobitRequest extends AbsRequest {

  case class YobitParams(sign: String, params: String)

  def nonce : Long = System.currentTimeMillis() / 1000 - Yobit.nonceFactor

  def addNonce(params: Map[String, String]): Map[String, String] = params + ("nonce" -> nonce.toString)

  def body(params: Map[String, String]): String = params.toSeq.sortBy(_._1).map(v => s"${v._1}=${v._2}").mkString("&")

  def toYobitParams(params : Map[String, String], secret: String) : YobitParams = {
    val withNonce = body(addNonce(params))
    YobitParams(toHex(signHmacSHA512(secret, withNonce), isCapital = false), withNonce)
  }

  def ownTrades(credentials: Credentials, pair: String) : YobitParams = ownTrades( credentials.signature, pair )

  def ownTrades(secret:String, pair: String) : YobitParams = {
    val params = Map(
      "method" -> "TradeHistory", //default order is DESC
      "pair" -> pair,
      "count" -> 100.toString
    )
    toYobitParams(params, secret)
  }

  def infoOrder(credentials: Credentials, orderId: String) : YobitParams = infoOrder(credentials.signature, orderId: String)

  def infoOrder(secret: String, orderId: String) : YobitParams = {
    val params = Map(
      "method" -> "OrderInfo",
      "order_id" -> orderId
    )
    toYobitParams(params, secret)
  }

  def newOrder(credentials: Credentials, pair: String, `type`: Side, price: BigDecimal, amount: BigDecimal) : YobitParams =
    newOrder(credentials.signature, pair, `type`, price, amount)

  def newOrder(secret: String, pair: String, `type`: Side, price: BigDecimal, amount: BigDecimal) : YobitParams = {
    val params = Map(
      "method" -> "Trade",
      "pair" -> pair,
      "type" -> `type`.toString,
      "rate" -> price.bigDecimal.toPlainString,
      "amount" -> amount.bigDecimal.toPlainString
    )
    toYobitParams(params, secret)
  }

  def cancelOrder(credentials: Credentials, orderId : String) : YobitParams = cancelOrder(credentials.signature, orderId)

  def cancelOrder(secret : String, orderId : String) : YobitParams = {
    val params = Map(
      "method" -> "CancelOrder",
      "order_id" -> orderId
    )
    toYobitParams(params, secret)
  }

  def activeOrders(credentials: Credentials, pair: String) : YobitParams = activeOrders(credentials.signature, pair)

  def activeOrders(secret: String, pair: String) : YobitParams = {
    val params = Map(
      "method" -> "ActiveOrders",
      "pair" -> pair
    )
    toYobitParams(params, secret)
  }

  def getInfo(credentials: Credentials) : YobitParams = getInfo(credentials.signature)

  def getInfo(secret: String) : YobitParams = {
    val params = Map(
      "method" -> "getInfo"
    )
    toYobitParams(params, secret)
  }

}
