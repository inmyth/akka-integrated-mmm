package me.mbcu.integrated.mmm.ops.fcoin

import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Bot, Offer, Status}
import me.mbcu.integrated.mmm.ops.fcoin.FcoinRequest.FcoinParams
import me.mbcu.integrated.mmm.ops.fcoin.FcoinState.FcoinState
import me.mbcu.integrated.mmm.ops.okex.OkexRest
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object FcoinActor{

  def toOffer(p: JsValue) : Offer = {
    val state = (p \ "state").as[FcoinState]
    val status = state match {
      case FcoinState.filled => Status.filled
      case FcoinState.submitted => Status.unfilled
      case FcoinState.partial_filled => Status.partialFilled
      case _ => Status.cancelled
    }
    new Offer(
      (p \ "id").as[String],
      (p \ "symbol").as[String],
      (p \ "side").as[Side],
      Some(status),
      Some((p \ "created_at").as[Long]),
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

}

class FcoinActor(bot: Bot) extends AbsRestActor(bot) with MyLogging{
  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._
  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  private var ws = StandaloneAhcWSClient()
  override val url: String = Fcoin.endpoint

  override def start(): Unit = setOp(Some(sender()))


  override def sendRequest(r: AbsRestActor.SendRequest): Unit = {

    r match {
      case a: GetTicker =>
        ws.url(Fcoin.endpoint.format(s"market/ticker/${bot.pair}"))
          .get()
          .map(response => parse(a, "get ticker", response.body[String]))

      case a: GetOrderbook => httpGet(a, FcoinRequest.getOrders(bot.credentials, bot.pair, FcoinState.submitted, a.page))

      case a : GetOwnPastTrades => httpGet(a, FcoinRequest.getOrders(bot.credentials, bot.pair, FcoinState.filled, 1))

      case a : NewOrder => httpPost(a, FcoinRequest.newOrder(bot.credentials, bot.pair, a.offer.side, a.offer.price, a.offer.quantity))

      case a : CancelOrder => httpPost(a, FcoinRequest.cancelOrder(bot.credentials, a.id))

      case a : GetOrderInfo => httpGet(a, FcoinRequest.getOrderInfo(bot.credentials, a.id))

    }

    def httpGet(a: SendRequest, f: FcoinParams): Unit = {
      ws.url(f.url)
        .addHttpHeaders("FC-ACCESS-KEY" -> bot.credentials.pKey)
        .addHttpHeaders("FC-ACCESS-SIGNATURE" -> f.sign)
        .addHttpHeaders("FC-ACCESS-TIMESTAMP" -> f.ts.toString)
        .get()
        .map(response => parse(a, f.url, response.body[String]))
    }

    def httpPost(a: SendRequest, r : FcoinParams): Unit = {
      ws.url(r.url)
        .addHttpHeaders("Content-Type" -> "application/json;charset=UTF-8")
        .addHttpHeaders("FC-ACCESS-KEY" -> bot.credentials.pKey)
        .addHttpHeaders("FC-ACCESS-SIGNATURE" -> r.sign)
        .addHttpHeaders("FC-ACCESS-TIMESTAMP" -> r.ts.toString)
        .post(r.js)
        .map(response => parse(a, r.params, response.body[String]))
    }
  }

  def parse(a: AbsRestActor.SendRequest, request: String, raw: String): Unit = {
    info(
      s"""
         |Request: $request
         |Response:
         |$raw
       """.stripMargin)

    op match {
      case Some(ref) =>
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
                case a : GetTicker =>
                  val lastPrice = (data \ "ticker").head.as[BigDecimal]
                  ref ! GotStartPrice(Some(lastPrice))

                case a : GetOwnPastTrades =>
                  val lastPrice = data.as[JsObject].fields.headOption match {
                    case Some(v) => (v._2 \  "executed_value").asOpt[BigDecimal]
                    case _ => None
                  }
                  ref ! GotStartPrice(lastPrice)

                case a: GetOrderbook =>
                  val items = data.as[List[JsValue]]
                  val offers = items.map(FcoinActor.toOffer)
                  ref ! GotOrderbook(offers, a.page, if (offers.size == 100) true else false)

                case a : NewOrder =>
                  val id = data.as[String]
                  ref ! GotOrderId(id)

                case a : CancelOrder => ref ! GotOrderCancelled(a.id)

                case a : GetOrderInfo => ref ! GotOrderInfo(FcoinActor.toOffer(data.as[JsValue]))

              }
            }

          case Failure(e) =>
            raw match  {
              case u : String if u.contains("<html>") => errorRetry(a, 0, raw)
              case _ => errorIgnore(0, s"Unknown FcoinActor#parse : $raw")
            }
        }

      case _ => error(s"No MainActor Reference FcoinActor#parse : $raw")

    }
  }

}
