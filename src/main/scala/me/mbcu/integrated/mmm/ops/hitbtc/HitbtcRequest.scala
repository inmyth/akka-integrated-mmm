package me.mbcu.integrated.mmm.ops.hitbtc

import me.mbcu.integrated.mmm.ops.common.AbsWsParser.SendWs
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsWsRequest, Credentials, Offer, Side}
import play.api.libs.json._

object HitbtcStatus extends Enumeration {
  type HitbtcStatus = Value
  val `new`, suspended, partiallyFilled, filled, canceled, expired = Value

  implicit val read = Reads.enumNameReads(HitbtcStatus)
  implicit val write = Writes.enumNameWrites
}

object HitbtcRequest extends AbsWsRequest{

  def sha256Hash(text: String) : String = String.format("%064x", new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"))))

  def requestId(pair: String, method: String, any: String = "") : String = s"$pair.$method.$any" // requestId has no length limit

  def clientOrderId(pair : String, side : Side): String = { // has to be at most 32 chars
    val prefix = s"$pair.$side."
    val random = scala.util.Random.alphanumeric.take(15).mkString
    val hash = sha256Hash(random + System.currentTimeMillis()).substring(0, 32 - prefix.length)
    s"$prefix$hash"
  }

  def pairFromId(id :String) : String = id.split("[.]").head

  def clientOrderIdFrom(id: String): String = {
    val p = id.split("[.]")
    s"${p(2)}.${p(3)}.${p(4)}"
  }

  def sideFromId(id : String) : Side = id match {
    case a if a contains ".sell." => Side.sell
    case _ => Side.buy
  }

  override def login(credentials: Credentials): SendWs = {
    val id = "login"
    SendWs(
      id,
      Json.parse(
        s"""
           |{
           |  "params": {
           |    "algo": "HS256",
           |    "pKey": "${credentials.pKey}",
           |    "nonce": "${credentials.nonce}",
           |    "signature": "${credentials.signature}"
           |  },
           |  "method": "login",
           |  "id": "$id"
           |}
     """.stripMargin)
    )

  }

  override val subscribe: SendWs = {
    val id = "subscribeReports"
    SendWs(
      id,
      Json.parse(
        s"""
           |{
           | "method": "subscribeReports",
           | "id": "$id",
           | "params": {}
           |}
     """.stripMargin)
    )
  }

  override def cancelOrder(orderId: String): SendWs = {
    val id = s"cancelOrder.$orderId"
    SendWs(id,
      Json.parse (
        s"""
           |{
           |  "method": "cancelOrder",
           |  "params": {
           |    "clientOrderId": "$orderId"
           |  },
           |  "id": "$id"
           |}
       """.stripMargin
      )
    )
  }

  override def newOrder(offer: Offer): SendWs = {
    val id = s"newOrder.${offer.id}"
    SendWs(id,
      Json.parse(
        s"""
           |{
           |  "method": "newOrder",
           |  "params": {
           |    "clientOrderId": "${offer.id}",
           |    "symbol": "${offer.symbol}",
           |    "side": "${offer.side.toString}",
           |    "price": "${offer.price.bigDecimal.toPlainString}",
           |    "quantity": "${offer.price.bigDecimal.toPlainString}"
           |  },
           |  "id": "$id"
           |}
       """.stripMargin)
    )
  }

  val marketSubs: String =
    """
      |{
      |  "method": "%s",
      |  "id": "%s",
      |  "params": {
      |    "symbol": "%s"
      |  }
      |}
    """.stripMargin

  override def subsTicker(pair: String): SendWs = {
    val id = s"subscribeTicker.$pair"
    SendWs(id, Json.parse(marketSubs.format("subscribeTicker", id, pair)))
  }


  override def unsubsTicker(pair: String): SendWs = {
    val id = s"unsubscribeTicker.$pair"
    SendWs(id, Json.parse(marketSubs.format("subscribeTicker", id, pair)))
  }

}


