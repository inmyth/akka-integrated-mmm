package me.mbcu.integrated.mmm.ops.btcalpha

import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.btcalpha.BtcalphaRequest.BtcalphaStatus.BtcalphaStatus
import me.mbcu.integrated.mmm.ops.btcalpha.BtcalphaRequest.{BtcalphaParams, BtcalphaStatus}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Offer, Status}
import me.mbcu.scala.MyLogging
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object BtcalphaActor {

  def parseTicker(js: JsValue): BigDecimal = BigDecimal((js.as[List[JsValue]].head \ "close").as[Double])

  def toOffer(js: JsValue): Offer =
    new Offer(
      (js \ "id").as[Long].toString,
      (js \ "pair").as[String],
      (js \ "type").as[Side],
      (js \ "status").as[BtcalphaStatus] match {
        case BtcalphaStatus.done => Status.filled
        case BtcalphaStatus.active => Status.active
        case BtcalphaStatus.cancelled => Status.cancelled
        case _ => Status.cancelled
      },
      createdAt = -1,
      None,
      (js \ "amount").as[BigDecimal], // this is the remaining amount not original amount
      (js \ "price").as[BigDecimal],
      None
    )

    /*
    [
    {
        "id": 28004855,
        "type": "buy",
        "pair": "NOAH_ETH",
        "price": "0.00000100",
        "amount": "1000.00000000",
        "status": 1
    }
]
     */

}

class BtcalphaActor extends AbsRestActor with MyLogging {
  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._

  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  private var ws = StandaloneAhcWSClient()

  override def start(): Unit = setOp(Some(sender()))

  override def sendRequest(r: AbsRestActor.SendRest): Unit = {
    r match {

      case a: GetTickerStartPrice => httpGet(a, BtcalphaRequest.getTickers(a.bot.pair)) // https://btc-alpha.com/api/charts/BTC_USD/1/chart/?format=json&limit=1

      case a: NewOrder => httpPost(a, BtcalphaRequest.newOrder(a.bot.credentials, a.bot.pair, a.offer.side, a.offer.price, a.offer.quantity))

      case a: CancelOrder => httpPost(a, BtcalphaRequest.cancelOrder(a.bot.credentials, a.offer.id))

      case a: GetActiveOrders => httpGet(a, BtcalphaRequest.getOrders(a.bot.credentials, a.bot.pair, BtcalphaStatus.active))

      case a: GetOwnPastTrades => httpGet(a, BtcalphaRequest.getOrders(a.bot.credentials, a.bot.pair, BtcalphaStatus.done))

      case a: GetOrderInfo => httpGet(a, BtcalphaRequest.getOrderInfo(a.bot.credentials, a.id))

    }

    def httpPost(a: SendRest, r: BtcalphaParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .addHttpHeaders("X-KEY" -> a.bot.credentials.pKey)
        .addHttpHeaders("X-SIGN" -> r.sign)
        .addHttpHeaders("X-NONCE" -> r.nonce)
        .withRequestTimeout(requestTimeoutSec seconds)
        .post(r.params)
        .map(response => parse(a, r.params, response.body[String]))
        .recover{
          case e: Exception => errorRetry(a, 0, e.getMessage, shouldEmail = false)
        }
    }

    def httpGet(a: SendRest, r: BtcalphaParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("X-KEY" -> a.bot.credentials.pKey)
        .addHttpHeaders("X-SIGN" -> r.sign)
        .addHttpHeaders("X-NONCE" -> r.nonce)
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
    val book = a.book

    a match {
      case p: NewOrder => op foreach (_ ! GotNewOrderId(s"prov-${System.currentTimeMillis()}", p.as, arriveMs, p))
      case _ =>
    }

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>

        val detail = (js \ "detail").asOpt[String]
        if (detail.isDefined) {
          detail.get match {
            case m if m contains "Wrong Nonce" => errorRetry(a, 0, m, false)
            case m if m contains "Incorrect authentication credentials" => errorShutdown(ShutdownCode.fatal, 0, s"$request $m")
            case _ => errorIgnore(-1, raw)
          }
        }
        else if (raw contains "Order already done"){
          /*
          {
            "order": [
                "Order already done"
            ]
          }
           */
          book ! GotOrderCancelled(a.as, arriveMs,a.asInstanceOf[CancelOrder])
          errorIgnore(0, "order has gone")
        }
        else if (raw contains "Out of balance"){
          errorIgnore(0, s"Not enough balance ${a.bot.pair}")
        }
        else {

          a match {

            case a: GetTickerStartPrice => book ! GotStartPrice(Some(BtcalphaActor.parseTicker(js)), arriveMs, a)

            case a: GetOwnPastTrades =>
              val orders = js.as[List[JsValue]].map(BtcalphaActor.toOffer)
              val price = if (orders.nonEmpty) Some(orders.head.price) else None
              book ! GotStartPrice(price, arriveMs, a)

            case a: GetActiveOrders =>
              val activeOrders = js.as[List[JsValue]].map(BtcalphaActor.toOffer)
              book ! GotActiveOrders(activeOrders, a.page, nextPage = false, arriveMs, a)

            case a: GetOrderInfo => book ! GotOrderInfo(BtcalphaActor.toOffer(js), arriveMs, a)

            case a: CancelOrder => book ! GotOrderCancelled(a.as, arriveMs, a)

            case a: NewOrder =>
              val id = (js \ "oid").as[Long].toString
              val serverMs = ((js \ "date").as[Double] * 1000).toLong
              book ! GotProvisionalOffer(id, serverMs, a.offer)
//              op foreach (_ ! GotNewOrderId(id, a.as, arriveMs, a))

            case _ => error(s"Unknown BtcalphaActor#parse : $raw")
          }
        }

      case Failure(r) =>
        raw match {
          case m if m contains "html" => errorRetry(a, 0, raw)
          case m if m contains "Ddos" => errorRetry(a, 0, m, shouldEmail = false)
          case m if m.isEmpty => errorRetry(a, 0, m, shouldEmail = false)
          case _ => errorIgnore(0, s"Unknown BtcalphaActor#parse : $raw")
        }
    }

  }
}
