package me.mbcu.integrated.mmm.ops.fcoin

import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Offer, Status}
import me.mbcu.integrated.mmm.ops.fcoin.FcoinRequest.FcoinParams
import me.mbcu.integrated.mmm.ops.fcoin.FcoinState.FcoinState
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsLookupResult, JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object FcoinActor {

  def toOffer(p: JsValue): Offer = {
    val state = (p \ "state").as[FcoinState]
    val status = state match {
      case FcoinState.filled => Status.filled
      case FcoinState.submitted => Status.active
      case FcoinState.partial_filled => Status.partiallyFilled
      case _ => Status.cancelled
    }
    new Offer(
      (p \ "id").as[String],
      (p \ "symbol").as[String],
      (p \ "side").as[Side],
      status,
      (p \ "created_at").as[Long],
      None,
      (p \ "amount").as[BigDecimal],
      (p \ "price").as[BigDecimal],
      Some((p \ "filled_amount").as[BigDecimal])
    )
    /*
     {
"id" :   "string" ,
"symbol" :   "string" ,
"type" :   "limit" ,
"side" :   "buy" ,
"price" :   "string" ,
"amount" :   "string" ,
"state" :   "submitted" ,
"executed_value" :   "string" ,
"fill_fees" :   "string" ,
"filled_amount" :   "string" ,
"created_at" :   0 ,
"source" :   "web"
}
]
 */
  }

  def parseFilled(data: JsLookupResult, lastCounterId: String): (Seq[Offer], Option[String]) = {
    val dexed = data.as[Seq[JsValue]].map(toOffer).filter(_.status == Status.filled).zipWithIndex

    if (dexed.isEmpty){
      (Seq.empty[Offer], None)
    } else {
      val latestCounterId = dexed.head._1.id
      val idPos = dexed.find(a => a._1.id == lastCounterId) match {
        case Some(pos) => pos._2
        case _ => Int.MaxValue
      }
      val uncountereds = dexed.filter(_._2 < idPos).sortWith(_._2 < _._2).map(_._1)
      (uncountereds, Some(latestCounterId))
    }
  }
}

class FcoinActor() extends AbsRestActor() with MyLogging {
  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._
  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  override val url: String = Fcoin.endpoint
  private var ws = StandaloneAhcWSClient()

  override def start(): Unit = setOp(Some(sender()))

  override def sendRequest(r: AbsRestActor.SendRest): Unit = {

    r match {
      case a: GetTickerStartPrice =>
        ws.url(Fcoin.endpoint.format(s"market/ticker/${a.bot.pair}"))
            .get()
            .map(response => parse(a, "get ticker", response.body[String]))

      case a: GetActiveOrders => httpGet(a, FcoinRequest.getOrders(a.bot.credentials, a.bot.pair, FcoinState.submitted, a.page))

      case a: GetFilledOrders => httpGet(a, FcoinRequest.getOrders(a.bot.credentials, a.bot.pair, FcoinState.filled, 1))

      case a: NewOrder => httpPost(a, FcoinRequest.newOrder(a.bot.credentials, a.bot.pair, a.offer.side, a.offer.price, a.offer.quantity))

      case a: CancelOrder => httpPost(a, FcoinRequest.cancelOrder(a.bot.credentials, a.id))

    }

    def httpGet(a: SendRest, f: FcoinParams): Unit = {
      ws.url(f.url)
        .addHttpHeaders("FC-ACCESS-KEY" -> a.bot.credentials.pKey)
        .addHttpHeaders("FC-ACCESS-SIGNATURE" -> f.sign)
        .addHttpHeaders("FC-ACCESS-TIMESTAMP" -> f.ts.toString)
        .get()
        .map(response => parse(a, f.url, response.body[String]))
    }

    def httpPost(a: SendRest, r: FcoinParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("Content-Type" -> "application/json;charset=UTF-8")
        .addHttpHeaders("FC-ACCESS-KEY" -> a.bot.credentials.pKey)
        .addHttpHeaders("FC-ACCESS-SIGNATURE" -> r.sign)
        .addHttpHeaders("FC-ACCESS-TIMESTAMP" -> r.ts.toString)
        .post(r.js)
        .map(response => parse(a, r.params, response.body[String]))
    }
  }

  def parse(a: AbsRestActor.SendRest, request: String, raw: String): Unit = {
    info(logResponse(a, raw))
    val arriveMs = System.currentTimeMillis()

    a match {
      case order: NewOrder => op foreach (_ ! GotNewOrder(arriveMs, order))
      case _ =>
    }

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>
        val rootStatus = (js \ "status").as[Int]
        if (rootStatus != 0) {
          val msg = (js \ "msg").as[String]
          msg match {
            case m if m.contains("api key check fail") => errorShutdown(ShutdownCode.fatal, 0, s"$request $m")
            case _ => errorIgnore(0, msg)
          }
        }
        else {
          val data = js \ "data"
          a match {
            case a: GetTickerStartPrice =>
              val lastPrice = (data \ "ticker").head.as[BigDecimal]
              a.book ! GotTickerStartPrice(Some(lastPrice), arriveMs, a)

            case a: GetFilledOrders =>
              val res = FcoinActor.parseFilled(data, a.lastCounterId)
              a.book ! GotUncounteredOrders(res._1, res._2, isSortedFromOldest = true, arriveMs, a)

            case a: GetActiveOrders =>
              val res = data.as[Seq[JsValue]].map(FcoinActor.toOffer)
              a.book ! GotActiveOrders(res, a.page, if (res.size == 100) true else false, arriveMs, a)

            case a: CancelOrder =>  // not handled

            case a: NewOrder => // not handled


          }
        }

      case Failure(e) =>
        raw match {
          case u: String if u.contains("<html>") => errorRetry(a, 0, raw)
          case _ => errorIgnore(0, s"Unknown FcoinActor#parse : $raw")
        }
    }


  }

}
