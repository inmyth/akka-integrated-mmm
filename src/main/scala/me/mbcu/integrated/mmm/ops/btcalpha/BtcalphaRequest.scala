package me.mbcu.integrated.mmm.ops.btcalpha

import me.mbcu.integrated.mmm.ops.btcalpha.BtcalphaRequest.BtcalphaStatus.BtcalphaStatus
import me.mbcu.integrated.mmm.ops.common.{AbsRequest, Credentials}
import play.api.libs.json._

object BtcalphaRequest extends AbsRequest {

  def getNonce: String = System.currentTimeMillis().toString

  object BtcalphaStatus extends Enumeration {
    type BtcalphaStatus = Value
    val active: BtcalphaRequest.BtcalphaStatus.Value = Value(1)
    val cancelled: BtcalphaRequest.BtcalphaStatus.Value = Value(2)
    val done: BtcalphaRequest.BtcalphaStatus.Value = Value(3)

    implicit val enumFormat = new Format[BtcalphaStatus] {
      override def reads(json: JsValue): JsResult[BtcalphaStatus] = json.validate[Int].map(BtcalphaStatus(_))
      override def writes(enum: BtcalphaStatus) = JsNumber(enum.id)
    }
  }

  case class BtcalphaParams(sign: String, nonce: String, url: String = "", params: String)

  def getOrders(credentials: Credentials, pair: String, status: BtcalphaStatus): BtcalphaParams = getOrders(credentials.pKey, credentials.signature, pair, status)

  def getOrders(key:String, secret: String, pair: String, status: BtcalphaStatus): BtcalphaParams = {
    val params = Map(
      "pair" -> pair,
      "status" -> status.id.toString
    )
    val sorted = sortToForm(params)
    BtcalphaParams(sign(key, secret, sorted), getNonce, Btcalpha.endpoint.format(s"orders/own/?$sorted"), sorted)
  }


  def sign(key: String, secret: String,  sorted: String): String = {
    val raw = key
    toHex(signHmacSHA256(secret, raw), isCapital = false)
  }

}
