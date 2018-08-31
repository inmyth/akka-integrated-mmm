package me.mbcu.integrated.mmm.ops.livecoin

import me.mbcu.integrated.mmm.ops.common.Status
import org.scalatest.FunSuite
import play.api.libs.json.{JsLookupResult, JsValue, Json}


class LivecoinActorTest extends FunSuite{

  test ("Livecoin to offer") {
    val a =
      """
        |{
        |    "totalRows": 3,
        |    "startRow": 0,
        |    "endRow": 2,
        |    "data": [
        |        {
        |            "id": 17444431201,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "CANCELLED",
        |            "issueTime": 1535380653251,
        |            "price": 0.05,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0.01,
        |            "commissionByTrade": 0,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535517866196
        |        },
        |        {
        |            "id": 17597454851,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "EXECUTED",
        |            "issueTime": 1535512555998,
        |            "price": 0.01,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0,
        |            "commissionByTrade": 7.5e-7,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535512555998
        |        },
        |        {
        |            "id": 17592585201,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "EXECUTED",
        |            "issueTime": 1535508494524,
        |            "price": 0.04175,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0,
        |            "commissionByTrade": 7.6e-7,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535508501398
        |        }
        |    ]
        |}
      """.stripMargin

        val js = Json.parse(a)
        val data = (js \ "data").as[List[JsValue]]
        val res = data.map(LivecoinActor.toOffer)
        assert(res.size === 3)
        assert(res.head.status === Status.cancelled)
  }


  test("Livecoin getUncounteredOffers") {

    val a =
      """
        |{
        |    "totalRows": 4,
        |    "startRow": 0,
        |    "endRow": 3,
        |    "data": [
        |        {
        |            "id": 17551577701,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "CANCELLED",
        |            "issueTime": 1535474938522,
        |            "price": 0.05,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0.01,
        |            "commissionByTrade": 0,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535518250557
        |        },
        |        {
        |            "id": 17444431201,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "CANCELLED",
        |            "issueTime": 1535380653251,
        |            "price": 0.05,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0.01,
        |            "commissionByTrade": 0,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535517866196
        |        },
        |        {
        |            "id": 17597454851,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "EXECUTED",
        |            "issueTime": 1535512555998,
        |            "price": 0.01,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0,
        |            "commissionByTrade": 7.5e-7,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535512555998
        |        },
        |        {
        |            "id": 17592585201,
        |            "currencyPair": "ETH/BTC",
        |            "goodUntilTime": 0,
        |            "type": "LIMIT_SELL",
        |            "orderStatus": "EXECUTED",
        |            "issueTime": 1535508494524,
        |            "price": 0.04175,
        |            "quantity": 0.01,
        |            "remainingQuantity": 0,
        |            "commissionByTrade": 7.6e-7,
        |            "bonusByTrade": 0,
        |            "bonusRate": 0,
        |            "commissionRate": 0.0018,
        |            "lastModificationTime": 1535508501398
        |        }
        |    ]
        |}
      """.stripMargin
    val js = Json.parse(a)
    val res = LivecoinActor.getUncounteredOrders(( js \ "data").as[List[JsValue]])
    assert(res.size === 2)
    assert(res.head.updatedAt.get < res(1).updatedAt.get)
  }
}
