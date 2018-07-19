package me.mbcu.integrated.mmm.ops.yobit

import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Offer, Status}
import me.mbcu.integrated.mmm.ops.yobit.YobitRequest.YobitParams
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object YobitActor {

  def parseForOrderId(js: JsValue): String = (js \ "return" \ "order_id").as[Long].toString

  def parseLastOwnTradePrice(js: JsValue): Option[BigDecimal] = {
    (js \ "return").as[JsObject].fields.headOption match {
      case Some(v) => (v._2 \ "rate").asOpt[BigDecimal]
      case _ => None
    }
  }

  def parseOrderInfo(js: JsValue): Offer = toOffer((js \ "return").as[JsObject].fields.head)

  def toOffer(head: (String, JsValue)): Offer = {
    val prc =
      if ((head._2 \ "start_amount").isDefined) {
        val price = (head._2 \ "start_amount").as[BigDecimal]
        val cumPrice = price - (head._2 \ "amount").as[BigDecimal]
        (price, Some(cumPrice))
      } else {
        val price = (head._2 \ "amount").as[BigDecimal]
        (price, None)
      }
    new Offer(
      head._1,
      (head._2 \ "pair").as[String],
      (head._2 \ "type").as[Side],
      (head._2 \ "status").as[Int] match {
        //status: 0 - active, 1 - fulfilled and closed, 2 - cancelled, 3 - cancelled after partially fulfilled.
        case 0 => Some(Status.unfilled)
        case 1 => Some(Status.filled)
        case 2 => Some(Status.cancelled)
        case _ => Some(Status.filled)
      },
      Some((head._2 \ "timestamp_created").as[String].toLong),
      None,
      prc._1,
      (head._2 \ "rate").as[BigDecimal],
      prc._2
    )
  }

  def parseActiveOrders(js: JsValue): Seq[Offer] = (js \ "return").as[JsObject].fields.map(toOffer)

  def parseTicker(js: JsValue, symbol: String): Option[BigDecimal] = Some((js \ symbol \ "last").as[BigDecimal])

}

class YobitActor() extends AbsRestActor() with MyLogging {

  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._

  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  val url: String = Yobit.endpoint
  val urlTrade: String = Yobit.endpointTrade
  private var ws = StandaloneAhcWSClient()

  override def start(): Unit = setOp(Some(sender()))

  override def sendRequest(r: AbsRestActor.SendRequest): Unit = {
    r match {
      case a: GetTicker =>
        ws.url(url.format("ticker", a.bot.pair))
          .get()
          .map(response => parse(a, "get ticker", response.body[String]))

      case a: NewOrder => tradeRequest(a, YobitRequest.newOrder(a.bot.credentials, a.bot.pair, a.offer.side, a.offer.price, a.offer.quantity))

      case a: CancelOrder => tradeRequest(a, YobitRequest.cancelOrder(a.bot.credentials, a.id))

      case a: GetOrderbook => tradeRequest(a, YobitRequest.activeOrders(a.bot.credentials, a.bot.pair))

      case a: GetOwnPastTrades => tradeRequest(a, YobitRequest.ownTrades(a.bot.credentials, a.bot.pair))

      case a: GetOpenOrderInfo => tradeRequest(a, YobitRequest.infoOrder(a.bot.credentials, a.id))

      case a: GetNewOrderInfo => tradeRequest(a, YobitRequest.infoOrder(a.bot.credentials, a.id))

    }

    def tradeRequest(a: SendRequest, r: YobitParams): Unit = {
      ws.url(s"$urlTrade")
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .addHttpHeaders("Key" -> a.bot.credentials.pKey)
        .addHttpHeaders("Sign" -> r.sign)
        .post(r.params)
        .map(response => parse(a, r.params, response.body[String]))
    }

  }

  def parse(a: AbsRestActor.SendRequest, request: String, raw: String): Unit = {
    info(
      s"""
         |Request: $request, As: ${a.as.getOrElse("")}, ${a.bot.exchange} : ${a.bot.pair}
         |Response:
         |$raw
       """.stripMargin)

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>
        val book = a.book
        a match {

          case t: GetTicker => book ! GotStartPrice(YobitActor.parseTicker(js, a.bot.pair))

          case _ =>
            (js \ "success").as[Int] match {
              case 0 =>
                val msg = (js \ "error").as[String]
                msg match {
                  case m if m.contains("invalid nonce (has already been used)") => errorRetry(a, 0, m, shouldEmail = false)
                  case m if m.contains("invalid sign") | m.contains("invalid key, sign, method or nonce") =>
                    errorShutdown(ShutdownCode.fatal, 0, s"$request $m")
                  case m if m.contains("The given order has already been cancelled.") => errorIgnore(0, msg)
                  case m if m.contains("Insufficient funds in wallet of the first currency of the pair") => errorIgnore(0, msg)
                  case _ => errorIgnore(0, msg)
                }

              case 1 =>
                a match {

                  case t: GetTicker => book ! GotStartPrice(YobitActor.parseTicker(js, a.bot.pair))

                  case t: GetOwnPastTrades => book ! GotStartPrice(YobitActor.parseLastOwnTradePrice(js))

                  case t: NewOrder => op foreach(_ ! GotNewOrderId(YobitActor.parseForOrderId(js), t.as, t))

                  case t: GetNewOrderInfo => op foreach(_ ! GotNewOrderInfo(YobitActor.parseOrderInfo(js), t.newOrder, book))

                  case t: GetOpenOrderInfo => book ! GotOpenOrderInfo(YobitActor.parseOrderInfo(js))
                  //{"success":0,"error":"A996DD2E"} // if order doesn't exist
                  case t: CancelOrder => book ! GotOrderCancelled(YobitActor.parseForOrderId(js), t.as)

                  case t: GetOrderbook =>
                    val activeOrders =
                      if ((js \ "return").isDefined) YobitActor.parseActiveOrders(js) else Seq.empty[Offer]
                    //{"success":1} // if there's no active order
                    book ! GotOrderbook(activeOrders, 1, nextPage = false)

                  case _ => error(s"Unknown YobitActor#parse : $raw")
                }
            }
        }

      case Failure(e) =>
        raw match {
          case u: String if u.contains("<html>") => errorRetry(a, 0, raw)
          case _ => errorIgnore(0, s"Unknown YobitActor#parse : $raw")
        }
    }
  }

}
