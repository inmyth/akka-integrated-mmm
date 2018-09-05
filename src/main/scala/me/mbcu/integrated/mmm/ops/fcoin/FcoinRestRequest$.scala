package me.mbcu.integrated.mmm.ops.fcoin

import java.util.Base64

import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestRequest, Credentials}
import me.mbcu.integrated.mmm.ops.fcoin.FcoinMethod.FcoinMethod
import me.mbcu.integrated.mmm.ops.fcoin.FcoinState.FcoinState
import play.api.libs.json.{Json, Reads, Writes}

object FcoinState extends Enumeration {
  type FcoinState = Value
  val submitted, partial_filled, partial_canceled, filled, canceled, pending_cancel = Value

  implicit val read = Reads.enumNameReads(FcoinState)
  implicit val write = Writes.enumNameWrites
}

object FcoinMethod extends Enumeration {
  type FcoinMethod = Value
  val POST, GET = Value
}

object FcoinRestRequest$ extends AbsRestRequest {

  case class FcoinParams(sign: String, params: String, ts: Long, url: String = "", js: String)

  def getOrders(secret: String, pair: String, state: FcoinState, after: Int) : FcoinParams = {
    val params = Map(
      "symbol" -> pair,
      "states" -> state.toString,
      "after" -> after.toString,
      "limit" -> 100.toString
    )
    sign(params, secret, FcoinMethod.GET, Fcoin.endpoint.format("orders"))
  }

  def getOrders(credentials: Credentials, pair: String, state: FcoinState, after: Int) : FcoinParams = getOrders(credentials.signature, pair, state, after)

  def newOrder(credentials: Credentials, pair: String, side: Side, price: BigDecimal, amount: BigDecimal) : FcoinParams
  = newOrder(credentials.signature, pair, side, price, amount)

  def newOrder(secret: String, pair: String, side: Side, price: BigDecimal, amount: BigDecimal) : FcoinParams = {
    val params = Map(
      "symbol" -> pair,
      "side" -> side.toString,
      "type" -> "limit",
      "price" -> price.bigDecimal.toPlainString,
      "amount" -> amount.bigDecimal.toPlainString
    )
    sign(params, secret, FcoinMethod.POST, Fcoin.endpoint.format("orders"))
  }

  def cancelOrder(secret : String, orderId : String): FcoinParams = {
    val params = Map(
      "order_id" -> orderId
    )

    val url = Fcoin.endpoint.format(s"orders/$orderId/submit-cancel")
    sign(params, secret, FcoinMethod.POST, url)
  }

  def cancelOrder(credentials: Credentials, orderId: String): FcoinParams = cancelOrder(credentials.signature, orderId)

  def getOrderInfo(secret: String, orderId: String): FcoinParams = {
    val params = Map(
      "order_id" -> orderId
    )
    val url = Fcoin.endpoint.format(s"orders/$orderId")
    sign(params, secret, FcoinMethod.GET, url)
  }

  def getOrderInfo(credentials: Credentials, orderId: String): FcoinParams = getOrderInfo(credentials.signature, orderId)

  def sign(params: Map[String, String], secret: String, method: FcoinMethod, baseUrl: String) : FcoinParams = {
    val ts = System.currentTimeMillis()
    val jsParams = Json.toJson(params).toString()
    val formParams = params.toSeq.sortBy(_._1).map(c => s"${c._1}=${c._2}").mkString("&")
    val template = "%s%s%s%s"
    val res = if (method.toString == "GET") {
      val getUrl = "%s%s%s".format(baseUrl, "?", formParams)
      val formatted = template.format(method, getUrl, ts, "")
      (formatted, getUrl)
    } else {
      val formatted = template.format(method, baseUrl, ts, formParams)
      (formatted, baseUrl)
    }
    val first = Base64.getEncoder.encodeToString(res._1.getBytes)
    val second = signHmacSHA1(secret, first)
    val signed = Base64.getEncoder.encodeToString(second)
    FcoinParams(signed, formParams, ts, res._2, jsParams)
  }

}
