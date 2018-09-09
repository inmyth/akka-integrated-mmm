package me.mbcu.integrated.mmm.ops.hitbtc

import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
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

  def requestId(pair: String, method: String) : String = s"$method.$pair" // requestId has no length limit

  def orderId(offer: Offer): String = { // has to be at most 32 chars
    val prefix = s"${offer.symbol}.${offer.side}."
    val random = scala.util.Random.alphanumeric.take(15).mkString
    val hash = sha256Hash(random + System.currentTimeMillis()).substring(0, 32 - prefix.length)
    s"$prefix$hash"
  }

  def pairFromId(id :String) : String = id.split("[.]")(1)

  def clientOrderIdFrom(id: String): String = {
    val p = id.split("[.]")
    s"${p(1)}.${p(2)}.${p(3)}"
  }

  def sideFromId(id : String) : Side = id match {
    case a if a contains ".sell." => Side.sell
    case _ => Side.buy
  }

  override def login(credentials: Credentials): SendWs = {
    val method = "login"
    val requestId = "login"
    SendWs(
      requestId,
      Json.parse(
        s"""
           |{
           |  "params": {
           |    "algo": "HS256",
           |    "pKey": "${credentials.pKey}",
           |    "nonce": "${credentials.nonce}",
           |    "signature": "${credentials.signature}"
           |  },
           |  "method": "$method",
           |  "id": "$requestId"
           |}
     """.stripMargin),
      As.Init

    )
  }

  override val subscribe: SendWs = {
    val method = "subscribeReports"
    val requestId = "subscribeReports"
    SendWs(
      requestId,
      Json.parse(
        s"""
           |{
           | "method": "$method",
           | "id": "$requestId",
           | "params": {}
           |}
     """.stripMargin),
      As.Init
    )
  }

  override def cancelOrder(orderId: String, as: As): SendWs = {
    val method = "cancelOrder"
    val requestId = HitbtcRequest.requestId(orderId, method)
    SendWs(requestId,
      Json.parse (
        s"""
           |{
           |  "method": "$method",
           |  "params": {
           |    "clientOrderId": "$orderId"
           |  },
           |  "id": "$requestId"
           |}
       """.stripMargin
      ),
      as
    )
  }

  override def newOrder(offer: Offer, as: As): SendWs = {
    val method = "newOrder"
    val requestId = HitbtcRequest.requestId(offer.id, method)

    SendWs(requestId,
      Json.parse(
        s"""
           |{
           |  "method": "$method",
           |  "params": {
           |    "clientOrderId": "${offer.id}",
           |    "symbol": "${offer.symbol}",
           |    "side": "${offer.side.toString}",
           |    "price": "${offer.price.bigDecimal.toPlainString}",
           |    "quantity": "${offer.quantity.bigDecimal.toPlainString}"
           |  },
           |  "id": "$requestId"
           |}
       """.stripMargin),
      as
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
    val method = "subscribeTicker"
    val requestId = HitbtcRequest.requestId(pair, method)
    SendWs(requestId, Json.parse(marketSubs.format(method, requestId, pair)), As.Init)
  }


  override def unsubsTicker(pair: String): SendWs = {
    val method = "unsubscribeTicker"
    val requestId = HitbtcRequest.requestId(pair, method)
    SendWs(requestId, Json.parse(marketSubs.format(method, requestId, pair)), As.Init)
  }

}


