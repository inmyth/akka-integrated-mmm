package me.mbcu.integrated.mmm.ops.livecoin

import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Offer, Side, Status}
import me.mbcu.integrated.mmm.ops.livecoin.LivecoinRequest.LivecoinState.LivecoinState
import me.mbcu.integrated.mmm.ops.livecoin.LivecoinRequest.{LivecoinParams, LivecoinState}
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsValue, Json}
import scala.concurrent.duration._
import scala.language.postfixOps
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object LivecoinActor {

  def toOffer(data: JsValue):Offer = {
    val state = (data \ "orderStatus").as[LivecoinState]

    val status = state match {
      case LivecoinState.CLOSED => Status.expired // returns EXECUTED and CANCELLED, only used in request
      case LivecoinState.EXECUTED => Status.filled
      case LivecoinState.OPEN => Status.active
      case LivecoinState.CANCELLED => Status.cancelled
      case LivecoinState.NOT_CANCELLED => Status.active
      case LivecoinState.PARTIALLY => Status.partiallyFilled // used in request
      case LivecoinState.PARTIALLY_FILLED => Status.partiallyFilled  // used in request
      case LivecoinState.PARTIALLY_FILLED_AND_CANCELLED => Status.partiallyFilled
      case LivecoinState.ALL => Status.active
      case _ => Status.cancelled
    }
    val quantity = (data \ "quantity").as[BigDecimal]
    new Offer(
      (data \ "id").as[Long].toString,
      (data \ "currencyPair").as[String],
      if ((data \ "type").as[String] contains "LIMIT_BUY") Side.buy else Side.sell,
      status,
      (data \ "issueTime").as[Long],
      Some((data \ "lastModificationTime").as[Long]),
      quantity,
      (data \ "price").as[BigDecimal],
      Some(quantity - (data \ "remainingQuantity").as[BigDecimal])
    )

/*
            "id": 17551577701,
            "currencyPair": "ETH/BTC",
            "goodUntilTime": 0,
            "type": "LIMIT_SELL",
            "orderStatus": "OPEN",
            "issueTime": 1535474938522,
            "price": 0.05,
            "quantity": 0.01,
            "remainingQuantity": 0.01,
            "commissionByTrade": 0,
            "bonusByTrade": 0,
            "bonusRate": 0,
            "commissionRate": 0.0018,
            "lastModificationTime": 1535474938522
 */
  }

  def getUncounteredOrders(list: List[JsValue]): Seq[Offer] =
    list.map(LivecoinActor.toOffer)
      .filter(_.status == Status.filled)
      .sortWith((p,q) => p.updatedAt.getOrElse(p.createdAt) < q.updatedAt.getOrElse(q.createdAt))

}

class LivecoinActor extends AbsRestActor with MyLogging{
  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._

  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  private var ws = StandaloneAhcWSClient()
  override def start(): Unit = setOp(Some(sender()))

  override def sendRequest(r: AbsRestActor.SendRest): Unit = {

    r match {

      case a: GetTickerStartPrice => httpGet(a, LivecoinRequest.getTicker(a.bot.pair))

      case a: GetActiveOrders => httpGet(a, LivecoinRequest.getActiveOrders(a.bot.credentials, a.bot.pair, LivecoinState.OPEN, (a.page - 1) * 100 ))

      case a: GetFilledOrders => httpGet(a, LivecoinRequest.getOwnTrades(a.bot.credentials, a.bot.pair, LivecoinState.CLOSED, a.lastCounterId.toLong))

      case a: NewOrder => httpPost(a, LivecoinRequest.newOrder(a.bot.credentials, a.offer.symbol, a.offer.side, a.offer.price, a.offer.quantity))

      case a: CancelOrder => httpPost(a, LivecoinRequest.cancelOrder(a.bot.credentials, a.offer.symbol, a.offer.id))
    }

    def httpPost(a: SendRest, r: LivecoinParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .addHttpHeaders("Sign" -> r.sign)
        .addHttpHeaders("API-key" -> a.bot.credentials.pKey)
        .withRequestTimeout(requestTimeoutSec seconds)
        .post(r.params)
        .map(response => parse(a, r.params, response.body[String]))
        .recover{
          case e: Exception => errorRetry(a, 0, e.getMessage, shouldEmail = false)
        }
    }

    def httpGet(a: SendRest, r: LivecoinParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .addHttpHeaders("Sign" -> r.sign)
        .addHttpHeaders("API-key" -> a.bot.credentials.pKey)
        .withRequestTimeout(requestTimeoutSec seconds)
        .get()
        .map(response => parse(a, r.params, response.body[String]))
        .recover{
          case e: Exception => errorRetry(a, 0, e.getMessage, shouldEmail = false)
        }
    }
  }

  def parse(a: AbsRestActor.SendRest, request: String, raw: String): Unit = {
    info(logResponse(a, raw))
    val arriveMs = System.currentTimeMillis()

    a match {
      case p: NewOrder => op foreach (_ ! GotNewOrderId("unused", p.as, arriveMs, p))
      case _ =>
    }

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>
          val success = js \ "success"
          if (success.isDefined) {
            if (!success.as[Boolean]) {
              if (raw.contains("invalid signature")) errorIgnore(-1, "invalid signature")
              else if (raw contains "incorrect key" ) errorShutdown(ShutdownCode.fatal, -1, "wrong API key: ${a.bot.pair}")
              else if (raw contains "Minimal amount is") errorIgnore(-1, "minimal amount too low: ${a.bot.pair}")
              else if (raw contains "insufficient funds")  errorIgnore(-1, s"insufficient funds: ${a.bot.pair}")
              else if (raw contains "Cannot get a connection, pool error Timeout waiting for idle object" ) errorRetry(a, 0, raw, shouldEmail = false)
              else if (raw contains "Service is under maintenance") errorRetry(a, 0, raw, shouldEmail = false)
              else errorIgnore(-1, raw)
            }
          }
          else {
            a match {

              case a: GetTickerStartPrice =>
                val lastPrice = (js \ "last").as[BigDecimal]
                a.book ! GotTickerStartPrice(Some(lastPrice), arriveMs, a)

              case a: GetFilledOrders =>
                val offers = LivecoinActor.getUncounteredOrders((js \ "data").as[List[JsValue]])
                val lastCounterId = if (offers.nonEmpty) Some((offers.last.updatedAt.getOrElse(offers.last.createdAt) + 1L).toString) else None
                a.book ! GotUncounteredOrders(offers, lastCounterId, isSortedFromOldest = true, arriveMs, a)

              case a: GetActiveOrders =>
                val res = (js \ "data").as[List[JsValue]].map(LivecoinActor.toOffer)
//                val isNextPage = (js \ "totalRows").as[Int] > (js \ "endRow").as[Int] // broken
                a.book ! GotActiveOrders(res, a.page, nextPage = false , arriveMs, a)
            }

          }

      case Failure(e) =>
        raw match {
          case m if m contains "html" => errorRetry(a, 0, raw, shouldEmail = false)
          case m if m.isEmpty => errorRetry(a, 0, m, shouldEmail = false)
          case _ => errorIgnore(0, s"Unknown LivecoinActor#parse : $raw")
        }
    }
  }

}
