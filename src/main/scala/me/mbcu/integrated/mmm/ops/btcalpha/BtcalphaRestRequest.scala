package me.mbcu.integrated.mmm.ops.btcalpha

import me.mbcu.integrated.mmm.ops.btcalpha.BtcalphaRestRequest.BtcalphaStatus.BtcalphaStatus
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestRequest, Bot, Credentials}
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json._

object BtcalphaRestRequest extends AbsRestRequest with MyLogging {

  def getNonce: String = System.currentTimeMillis().toString

  object BtcalphaStatus extends Enumeration {
    type BtcalphaStatus = Value
    val active: BtcalphaRestRequest.BtcalphaStatus.Value = Value(1)
    val cancelled: BtcalphaRestRequest.BtcalphaStatus.Value = Value(2)
    val done: BtcalphaRestRequest.BtcalphaStatus.Value = Value(3)

    implicit val enumFormat: Format[BtcalphaStatus] = new Format[BtcalphaStatus] {
      override def reads(json: JsValue): JsResult[BtcalphaStatus] = json.validate[Int].map(BtcalphaStatus(_))
      override def writes(enum: BtcalphaStatus) = JsNumber(enum.id)
    }
  }

  def sanitizeSecret(s: String): String =  {
    val res = StringContext treatEscapes s
    info(res)
    res
  }

  case class BtcalphaParams(sign: String, nonce: String, url: String = "", params: String)

  def getTickers(pair: String): String = Btcalpha.endpoint.format(s"charts/$pair/1/chart/?format=json&limit=1")

  def getOrders(credentials: Credentials, pair: String, status: BtcalphaStatus): BtcalphaParams = getOrders(credentials.pKey, credentials.signature, pair, status)

  def getOrders(key:String, secret: String, pair: String, status: BtcalphaStatus): BtcalphaParams = {
    val params = Map(
      "pair" -> pair,
      "status" -> status.id.toString
    )
    val sorted = sortToForm(params)
    BtcalphaParams(sign(key, secret), getNonce, Btcalpha.endpoint.format(s"v1/orders/own/?$sorted"), sorted)

  }

  def newOrder(credentials: Credentials, pair: String, side: Side, price: BigDecimal, amount: BigDecimal) : BtcalphaParams =
    newOrder(credentials.pKey, credentials.signature, pair, side, price, amount)

  def newOrder(key: String, secret: String, pair: String, side: Side, price: BigDecimal, amount: BigDecimal) : BtcalphaParams = {
  //67a6cfe5-c6fc-46fd-8e4d-b7f87533fdc7amount=1000&pair=NOAH_ETH&price=0.000001&type=buy
    val params = Map(
      "pair" -> pair,
      "type" -> side.toString,
      "price" -> price.bigDecimal.toPlainString,
      "amount" -> amount.bigDecimal.toPlainString
    )

    val sorted = sortToForm(params)
    val signed = sign(arrangePost(key, sorted), secret)
    BtcalphaParams(signed, getNonce, Btcalpha.endpoint.format("v1/order/"), sorted)
  }

  def cancelOrder(credentials: Credentials, id: String) : BtcalphaParams = cancelOrder(credentials.pKey, credentials.signature, id)

  def cancelOrder(key: String, secret: String, id: String) : BtcalphaParams = {
    val params = s"order=$id"
    val signed = sign(arrangePost(key, params), secret)
    BtcalphaParams(signed, getNonce, Btcalpha.endpoint.format("v1/order-cancel/"), params)
  }

  def getOrderInfo(credentials: Credentials, id: String) : BtcalphaParams = getOrderInfo(credentials.pKey, credentials.signature, id)

  def getOrderInfo(key: String, secret: String, id: String) : BtcalphaParams = {
    val signed = sign(key, secret)
    BtcalphaParams(signed, getNonce, Btcalpha.endpoint.format(s"v1/order/$id/"), id)
  }

  def arrangePost(key: String, sorted: String): String = s"$key$sorted"

  def sign(payload: String, secret: String): String = toHex(signHmacSHA256(sanitizeSecret(secret), payload), isCapital = false)

}
