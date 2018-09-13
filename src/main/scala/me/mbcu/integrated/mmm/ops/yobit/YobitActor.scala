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
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object YobitActor {

  def parseForOrderId(js: JsValue): String = (js \ "return" \ "order_id").as[Long].toString

  def parseForServerTs(js: JsValue): Long = (js \ "return" \ "server_time").as[Long]

  def parseLastOwnTradePrice(js: JsValue): Option[BigDecimal] = {
    (js \ "return").as[JsObject].fields.headOption match {
      case Some(v) => (v._2 \ "rate").asOpt[BigDecimal]
      case _ => None
    }
  }


  val activeToOffer: Tuple2[Long, JsValue] => Offer = (head: Tuple2[Long, JsValue]) => {
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
      head._1.toString,
      (head._2 \ "pair").as[String],
      (head._2 \ "type").as[Side],
      (head._2 \ "status").as[Int] match {
        //status: 0 - active, 1 - fulfilled and closed, 2 - cancelled, 3 - cancelled after partially fulfilled.
        case 0 => Status.active
        case 1 => Status.filled
        case 2 => Status.cancelled
        case 3 => Status.partiallyFilled
        case _ => Status.cancelled
      },
      (head._2 \ "timestamp_created").as[String].toLong * 1000L,
      None,
      prc._1,
      (head._2 \ "rate").as[BigDecimal],
      prc._2
    )
  }

  def sumOffersOnAmount(a: Offer)(b: Offer): Offer =
    new Offer(a.id, a.symbol, a.side, a.status, a.createdAt, a.updatedAt, a.quantity + b.quantity, a.price, None)


  val filledToOffer : Tuple2[Long, JsValue] => Offer = (head: Tuple2[Long, JsValue]) => {
      new Offer(
        (head._2 \ "order_id").as[String],
        (head._2 \ "pair").as[String],
        (head._2 \ "type").as[Side],
        Status.filled,
        (head._2 \ "timestamp").as[String].toLong * 1000L,
        None,
        (head._2 \ "amount").as[BigDecimal],
        (head._2 \ "rate").as[BigDecimal],
        None
      )
  }

  def parseFilled(js: JsValue, lastCounterId: Long): (Seq[Offer], Option[String]) = {
    val a = parseReturn(js)
    val latestCounterId = a.head._1
    val uncountereds = a.map(c => (c._1.toLong, c._2))
      .filter(_._1 > lastCounterId)
      .map(YobitActor.filledToOffer).groupBy(_.id)
      .map(d => {
        val sumQuantity = d._2.map(_.quantity).sum
        val a = d._2.head
        val v = new Offer(a.id, a.symbol, a.side, a.status, a.createdAt, a.updatedAt, sumQuantity, a.price, None)
        val k = a.id.toLong
        (k,v)
      })
      .toSeq
      .sortWith(_._1 < _._1)
      .map(_._2)
    (uncountereds, Some(latestCounterId))
  }

  def parseOrders(js: JsValue, t:Tuple2[Long, JsValue] => Offer): Seq[Offer] = parseReturn(js).map(c => (c._1.toLong, c._2)).map(t)


  def parseReturn(js: JsValue): Seq[(String, JsValue)] = (js \ "return").as[JsObject].fields

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

  override def sendRequest(r: AbsRestActor.SendRest): Unit = {
    r match {
      case a: GetTickerStartPrice => httpGet(a, url.format("ticker", a.bot.pair))

      case a: NewOrder => httpPost(a, YobitRequest.newOrder(a.bot.credentials, a.bot.pair, a.offer.side, a.offer.price, a.offer.quantity))

      case a: CancelOrder => httpPost(a, YobitRequest.cancelOrder(a.bot.credentials, a.offer.id))

      case a: GetActiveOrders => httpPost(a, YobitRequest.activeOrders(a.bot.credentials, a.bot.pair))

      case a: GetOwnPastTrades => httpPost(a, YobitRequest.ownTrades(a.bot.credentials, a.bot.pair))

      case a: GetOrderInfo => httpPost(a, YobitRequest.infoOrder(a.bot.credentials, a.id))

    }

     def httpGet(a: SendRest, url: String): Unit =
       ws.url(url)
         .withRequestTimeout(requestTimeoutSec seconds)
         .get()
         .map(response => parse(a, url, response.body[String]))
         .recover{
           case e: Exception => errorRetry(a, 0, e.getMessage, shouldEmail = false)
         }


    def httpPost(a: SendRest, r: YobitParams): Unit =
      ws.url(s"$urlTrade")
        .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
        .addHttpHeaders("Key" -> a.bot.credentials.pKey)
        .addHttpHeaders("Sign" -> r.sign)
        .withRequestTimeout(requestTimeoutSec seconds)
        .post(r.params)
        .map(response => parse(a, r.params, response.body[String]))
        .recover{
          case e: Exception => errorRetry(a, 0, e.getMessage, shouldEmail = false)
        }

  }

  def parse(a: AbsRestActor.SendRest, request: String, raw: String): Unit = {
    info(logResponse(a, raw))
    val arriveMs = System.currentTimeMillis()

    a match {
      case p: NewOrder => op foreach (_ ! GotNewOrderId(s"prov-${System.currentTimeMillis()}", p.as, arriveMs, p))
      case _ =>
    }

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>
        val book = a.book
        a match {

          case t: GetTickerStartPrice =>  book ! GotStartPrice(YobitActor.parseTicker(js, a.bot.pair), arriveMs, t)

          case _ =>
            (js \ "success").as[Int] match {
              case 0 =>
                val msg = (js \ "error").as[String]
                msg match {
                  case m if m.contains("invalid nonce (has already been used)") => errorRetry(a, 0, m, shouldEmail = false)
                  case m if m.contains("invalid sign") | m.contains("invalid key, sign, method or nonce") =>
                    errorShutdown(ShutdownCode.fatal, 0, s"$request $m")
                  case m if m.contains("The given order has already been cancelled.") =>
                    book ! GotOrderCancelled(a.as, arriveMs,a.asInstanceOf[CancelOrder])
                    errorIgnore(0, msg)
                  case m if m.contains("The given order has already been closed and cannot be cancelled.") =>
                    book ! GotOrderCancelled(a.as, arriveMs, a.asInstanceOf[CancelOrder])
                    errorIgnore(0, msg)
                  case m if m.contains("Insufficient funds in wallet of the first currency of the pair") => errorIgnore(0, msg)
                  case _ => errorIgnore(0, msg)
                }

              case 1 =>
                a match {

                  case t: GetOwnPastTrades => book ! GotStartPrice(YobitActor.parseLastOwnTradePrice(js), arriveMs, t)

                  case t: GetActiveOrders =>
                    val activeOrders = if ((js \ "return").isDefined) YobitActor.parseOrders(js, YobitActor.activeToOffer) else Seq.empty[Offer]
                    //{"success":1} // if there's no active order
                    book ! GotActiveOrders(activeOrders, t.page, nextPage = false, arriveMs, t)

                  case t: GetOrderInfo => book ! GotOrderInfo(YobitActor.parseOrders(js, YobitActor.activeToOffer).head, arriveMs, t )
                  //{"success":0,"error":"A996DD2E"} // if order doesn't exist

                  case t: CancelOrder => book ! GotOrderCancelled(t.as, arriveMs, t)

                  case t: NewOrder =>
                    val id = YobitActor.parseForOrderId(js)
                    val serverMs = YobitActor.parseForServerTs(js) * 1000
                    t.book ! GotProvisionalOffer(id, serverMs, t.offer)
                    op foreach(_ ! GotNewOrderId(id, t.as, arriveMs, t))

                  case _ => error(s"Unknown YobitActor#parse : $raw")
                }
            }
        }

      case Failure(e) =>
        raw match {
          case m if m contains "<!DOCTYPE html>"  => errorRetry(a, 0, raw)
          case m if m contains "Ddos" => errorRetry(a, 0, m, shouldEmail = false)
          case m if m.isEmpty => errorRetry(a, 0, m, shouldEmail = false)
          case _ => errorIgnore(0, s"Unknown YobitActor#parse : $raw")
        }
    }
  }

}
