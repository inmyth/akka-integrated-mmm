package me.mbcu.integrated.mmm.ops.okex

import akka.actor.ActorRef
import akka.dispatch.ExecutionContexts.global
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorIgnore, ErrorRetryRest, ErrorShutdown, ShutdownCode}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.Status.Status
import me.mbcu.integrated.mmm.ops.common.{AbsRestActor, Bot, Offer}
import me.mbcu.integrated.mmm.ops.okex.models.{OkexRequest, OkexStatus}
import me.mbcu.integrated.mmm.utils.MyLogging
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

class OkexRestActor(bot: Bot) extends AbsRestActor(bot) with MyLogging {
  import play.api.libs.ws.DefaultBodyReadables._
  import play.api.libs.ws.DefaultBodyWritables._
  private var op : Option[ActorRef] = None
  private implicit val materializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = global
  val OKEX_ERRORS: Map[Int, String] = OkexRest.OKEX_ERRORS
  private var ws = StandaloneAhcWSClient()
  val url: String = OkexRest.endpoint

  override def start(): Unit = op = Some(sender())

  override def sendRequest(r: SendRequest): Unit = {

      r match {
        case a: NewOrder =>
          ws.url(s"$url/trade.do")
            .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
            .post(stringifyXWWWForm(OkexRequest.restNewOrder(bot.credentials, bot.pair, a.offer.side, a.offer.price, a.offer.quantity)))
            .map(response => parse(a, response.body[String]))

        case a: CancelOrder =>
          ws.url(s"$url/cancel_order.do")
            .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
            .post(stringifyXWWWForm(OkexRequest.restCancelOrder(bot.credentials, bot.pair, a.id)))
            .map(response => parse(a, response.body[String]))

        case a: GetOrderbook =>
          ws.url(s"$url/order_history.do")
            .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
            .post(stringifyXWWWForm(OkexRequest.restOwnTrades(bot.credentials, bot.pair, OkexStatus.unfilled, a.page)))
            .map(response => parse(a, response.body[String]))

        case a: GetOwnPastTrades =>
          ws.url(s"$url/order_history.do")
            .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
            .post(stringifyXWWWForm(OkexRequest.restOwnTrades(bot.credentials, bot.pair, OkexStatus.filled, 1)))
            .map(response => parse(a, response.body[String]))

        case a: GetOrderInfo =>
          ws.url(s"$url/order_info.do")
            .addHttpHeaders("Content-Type" -> "application/x-www-form-urlencoded")
            .post(stringifyXWWWForm(OkexRequest.restInfoOrder(bot.credentials, bot.pair, a.id)))
            .map(response => parse(a, response.body[String]))

        case a: GetTicker =>
          ws.url(s"$url/ticker.do")
            .addQueryStringParameters(OkexRequest.restTicker(bot.pair).toSeq: _*)
            .get()
            .map(response => parse(a, response.body[String]))
      }
  }

  def parse(a: SendRequest, raw: String): Unit = {
    info(
      s"""Response:
         |$raw
       """.stripMargin)
    op match {
      case Some(ref) =>
        val x = Try(Json parse raw)
        x match {
          case Success(js) =>
            if ((js \ "success").isDefined){
              val code = (js \ "error_code").as[Int]
              val msg = OKEX_ERRORS.getOrElse(code, " code list : https://github.com/okcoin-okex/API-docs-OKEx.com/blob/master/API-For-Spot-EN/Error%20Code%20For%20Spot.md")
              pipeErrors(code, msg, a)
            }
            else{
              a match  {
                case t : GetTicker =>
                  val lastTrade = (js \ "ticker" \ "last").as[BigDecimal]
                  ref ! GotStartPrice(Some(lastTrade))

                case t : GetOwnPastTrades =>
                  val trade = (js \ "orders").as[JsArray].head
                  if (trade.isDefined) ref ! GotStartPrice(Some((trade \ "price").as[BigDecimal])) else ref ! GotStartPrice(None)

                case t : GetOrderbook =>
                  val orders = (js \ "orders").as[List[JsValue]]
                  val res = orders.map(toOffer)
                  val currentPage = (js \ "currency_page").as[Int]
                  val nextPage =  if ((js \ "page_length").as[Int] > 200) true else false
                  ref ! GotOrderbook(res, currentPage, nextPage)

                case t: NewOrder => ref ! GotOrderId((js \ "order_id").as[Long].toString)

                case t: CancelOrder => ref ! GotOrderCancelled((js \ "order_id").as[String])

                case t: GetOrderInfo =>
                  val order = (js \ "orders").as[JsArray].head
                  if (order.isDefined) ref ! GotOrderInfo(toOffer(order.as[JsValue])) else errorShutdown(ShutdownCode.fatal, -10, s"OkexParser#parseRest Undefined orderInfo $raw")

                case _ => error(s"Unknown OkexRestActor#parseRest : $raw")
              }
            }

          case Failure(e) => raw match  {
            case u : String if u.contains("<html>") =>
              val code = -1000
              val msg =
                s"""${OKEX_ERRORS(code)}
                   |$raw
             """.stripMargin
              pipeErrors(code, msg, a)
            case _ => error(s"Unknown OkexRestActor#parseRest : $raw")
          }
        }
      case _=> error(s"No MainActor Reference OkexRestActor#parseRest : $raw")
    }

  }

  private def pipeErrors(code : Int, msg : String, sendRequest: SendRequest): Unit = {
    code match {
      case 10009 | 10010 | 10011 | 10014 | 10016 | 10024 | 1002  => errorIgnore(code, msg)
      case 20100 | 10007 | 10005 | -1000 => errorRetry(sendRequest, code, msg)
      case _ => errorShutdown(ShutdownCode.fatal, code, msg)
    }
  }

  override def errorRetry(sendRequest: SendRequest, code: Int, msg: String): Unit = op foreach(_ ! ErrorRetryRest(sendRequest, code, msg))

  override def errorShutdown(shutdownCode: ShutdownCode, code: Int, msg: String): Unit = op foreach(_ ! ErrorShutdown(shutdownCode, code, msg))

  override def errorIgnore(code: Int, msg: String): Unit = op foreach(_ ! ErrorIgnore(code, msg))

  val toOffer :  JsValue => Offer = (data: JsValue) =>
    new Offer(
      (data \ "order_id").as[Long].toString,
      (data \ "symbol").as[String],
      (data \ "type").as[Side],
      (data \ "status").asOpt[Status],
      (data \ "create_date").asOpt[Long],
      None,
      (data \ "amount").as[BigDecimal],
      (data \ "price").as[BigDecimal],
      (data \ "deal_amount").asOpt[BigDecimal]
    )


}
