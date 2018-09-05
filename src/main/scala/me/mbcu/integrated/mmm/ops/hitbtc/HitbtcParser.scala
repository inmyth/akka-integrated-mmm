package me.mbcu.integrated.mmm.ops.hitbtc

import akka.actor.ActorRef
import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsWsParser._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{AbsWsParser, Offer, Status}
import me.mbcu.integrated.mmm.ops.hitbtc.HitbtcStatus.HitbtcStatus
import org.joda.time.DateTime
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

object HitbtcParser {

  def toOffer(js: JsValue): Offer = {
    Offer(
      (js \ "clientOrderId").as[String],
      (js \ "symbol").as[String],
      (js \ "side").as[Side],
      (js \ "status").as[HitbtcStatus] match {
        case HitbtcStatus.`new` => Status.active
        case HitbtcStatus.canceled => Status.cancelled
        case HitbtcStatus.expired => Status.cancelled
        case HitbtcStatus.filled => Status.filled
        case HitbtcStatus.partiallyFilled => Status.partiallyFilled
        case _ => Status.cancelled
      },
      DateTime.parse((js \ "createdAt").as[String]).getMillis,
      Some(DateTime.parse((js \ "updatedAt").as[String]).getMillis),
      (js \ "quantity").as[BigDecimal],
      (js \ "price").as[BigDecimal],
      Some((js \ "cumQuantity").as[BigDecimal])
    )

    /*
      "id": "4345613661",
      "clientOrderId": "57d5525562c945448e3cbd559bd068c3",
      "symbol": "BCCBTC",
      "side": "sell",
      "status": "new",
      "type": "limit",
      "timeInForce": "GTC",
      "quantity": "0.013",
      "price": "0.100000",
      "cumQuantity": "0.000",
      "createdAt": "2017-10-20T12:17:12.245Z",
      "updatedAt": "2017-10-20T12:17:12.245Z",
      "reportType": "status"
     */
  }

}

class HitbtcParser(op: ActorRef) extends AbsWsParser(op){

  override def parse(raw: String, botMap: Map[String, ActorRef]): Unit = {
    info(
      s"""Raw response
         |$raw
        """.stripMargin)

    val x = Try(Json parse raw)
    x match {
      case Success(js) =>
          if ((js \ "error").isDefined){
            val error = (js \ "error").as[RPCError]
            val id = (js \ "id").as[String]
            val book = botMap(HitbtcRequest.pairFromId(id))
            error.code match {
              case 2011 | 10001 | 20001 | 20002 => book ! RemoveOfferWs(id)
              case 20008 | 20004 => book ! RemoveOfferWs(id) // duplicate clientOrderId
              case 2001 | 2002 => errorShutdown(ShutdownCode.fatal, error.code, error.message.getOrElse("Currency / symbol not found"))
              case 1001 | 1002 | 1003 | 1004 | 403 => errorShutdown(ShutdownCode.fatal, error.code, error.message.getOrElse("Authentication failed"))
              case 429 | 500 | 503 | 504 => book ! RetryPendingWs(id)
              case _ => // ErrorNonAffecting(error, id)
            }
            /*
            403 	401 	Action is forbidden for account
            429 	429 	Too many requests 	Action is being rate limited for account
            500 	500 	Internal Server Error
            503 	503 	Service Unavailable 	Try it again later
            504 	504 	Gateway Timeout 	Check the result of your request later
            1001 	401 	Authorisation required
            1002 	401 	Authorisation failed
            1003 	403 	Action is forbidden for this API key 	Check permissions for API key
            1004 	401 	Unsupported authorisation method 	Use Basic authentication
            2001 	400 	Symbol not found
            2002 	400 	Currency not found
            20001 	400 	Insufficient funds 	Insufficient funds for creating order or any account operation
            20002 	400 	Order not found 	Attempt to get active order that not existing, filled, canceled or expired. Attempt to cancel not existing order. Attempt to cancel already filled or expired order.
            20003 	400 	Limit exceeded 	Withdrawal limit exceeded
            20004 	400 	Transaction not found 	Requested transaction not found
            20005 	400 	Payout not found
            20006 	400 	Payout already committed
            20007 	400 	Payout already rolled back
            code":2011,"message":"Quantity too low
            {"code":10001,"message":"\"price\" must be a positive number
            code":2011,"message":"Quantity too low","description":"Minimum quantity 1"
          */
          }
          else {
            if((js \ "method").isDefined && (js \ "params").isDefined){ // stream report comes here
              val method = (js \ "method").as[String]
              val params = js \ "params"
              method match {

                case "activeOrders" =>
                  params.as[List[JsValue]].map(HitbtcParser.toOffer)
                      .groupBy(_.symbol)
                      .foreach(p => botMap(p._1) ! GotActiveOrdersWs(p._2, "act"))

                case "report" =>
                  val offer = HitbtcParser.toOffer(params.as[JsValue])
                  botMap(offer.symbol) ! GotOfferWs(offer, offer.id)

                case "ticker" =>
                  val pair = (params \ "symbol").as[String]
                  val last = (params \ "last").as[BigDecimal]
                  botMap(pair) ! GotStartPriceWs(Some(last), "subscribeReports")

                case _ =>
              }

            }
            else if ((js \ "id").isDefined){ // RPC response
              val id = (js \ "id").as[String]
              id match {
                case a if a equals "login" =>  op ! LoggedIn(a) // {"jsonrpc":"2.0","result":true,"id":"login"}
                case a if a equals "subscribeReports" =>  op ! GotSubscribe(a)
                case _ =>  botMap(HitbtcRequest.pairFromId(id)) ! RemovePendingWs(id)
              }
            }
          }

      case Failure(e) => errorIgnore(-1, s"Cannot parse into Json: $raw", shouldEmail = true)

    }

  }

}
