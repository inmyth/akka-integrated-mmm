package me.mbcu.integrated.ops.yobit

import me.mbcu.integrated.mmm.ops.common.{Side, Status}
import me.mbcu.integrated.mmm.ops.yobit.YobitActor
import org.scalatest.FunSuite
import play.api.libs.json.{JsObject, JsValue, Json}

class YobitActorTest extends FunSuite{


  test("test parse TradeHistory") {
    val test =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "200124828": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "amount": 10,
        |      "rate": 0.00008871,
        |      "order_id": "106374962475788",
        |      "is_your_order": 1,
        |      "timestamp": "1530208364"
        |    },
        |    "200124827": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "amount": 10,
        |      "rate": 0.00008843,
        |      "order_id": "106374962466856",
        |      "is_your_order": 1,
        |      "timestamp": "1530208185"
        |    },
        |    "200124826": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "amount": 10,
        |      "rate": 0.00008843,
        |      "order_id": "106374962464038",
        |      "is_your_order": 1,
        |      "timestamp": "1530208129"
        |    }
        |  }
        |}
      """.stripMargin

    val test2 =
      """
        |{
        |    "success": 1,
        |    "return": {
        |    }
        |}
      """.stripMargin

    assert(YobitActor.parseLastOwnTradePrice(Json.parse(test)) === Some(BigDecimal("0.00008871")))

  }


  test("test parse New Order") {
    val test =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "received": 0,
        |    "remains": 10,
        |    "order_id": 106374964977757,
        |    "funds": {
        |      "eth": 0.28562179,
        |      "trx": 40
        |    },
        |    "funds_incl_orders": {
        |      "eth": 0.28645345,
        |      "trx": 40
        |    },
        |    "server_time": 1530257142
        |  }
        |}
      """.stripMargin

    assert(YobitActor.parseForOrderId(Json.parse(test)) === "106374964977757")

  }

  test("test parse Cancel Order" ) {
    val test1 =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "order_id": 106374965614339,
        |    "funds": {
        |      "eth": 0.28279105,
        |      "trx": 70
        |    },
        |    "funds_incl_orders": {
        |      "eth": 0.28362271,
        |      "trx": 70
        |    },
        |    "server_time": 1530270657
        |  }
        |}
      """.stripMargin

  }

  test ("test parse Order Info") {
    val testFilled =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "106374964997059": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "start_amount": 10,
        |      "amount": 0,
        |      "rate": 0.00010843,
        |      "timestamp_created": "1530257526",
        |      "status": 1
        |    }
        |  }
        |}
      """.stripMargin

    val testCanceled =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "106374965614339": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "start_amount": 10,
        |      "amount": 10,
        |      "rate": 0.00008137,
        |      "timestamp_created": "1530270551",
        |      "status": 2
        |    }
        |  }
        |}
      """.stripMargin

    val filled = YobitActor.parseOrderInfo(Json.parse(testFilled))
    val canceled = YobitActor.parseOrderInfo(Json.parse(testCanceled))

    assert(filled.status === Some(Status.filled))
    assert(filled.side === Side.buy)
    assert(canceled.status === Some(Status.cancelled))
    assert(canceled.cumQuantity === Some(BigDecimal("0")))

  }

  test ("test parse Active Orders") {
    val test =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "106374965790953": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "amount": 10,
        |      "rate": 0.00007875,
        |      "timestamp_created": "1530274556",
        |      "status": 0
        |    },
        |    "106374962480840": {
        |      "pair": "trx_eth",
        |      "type": "buy",
        |      "amount": 10,
        |      "rate": 0.000083,
        |      "timestamp_created": "1530208463",
        |      "status": 0
        |    }
        |  }
        |}
      """.stripMargin

    val res = YobitActor.parseActiveOrders(Json.parse(test))
    assert(res.head.id === "106374965790953")
    assert(res.head.status === Some(Status.unfilled))
    assert(res(1).id === "106374962480840")
  }

  test ("test parse Ticker") {
    val test =
      """
        |{
        |  "ltc_btc": {
        |    "high": 0.01302443,
        |    "low": 0.01240466,
        |    "avg": 0.01271454,
        |    "vol": 187.61157375,
        |    "vol_cur": 14676.32497762,
        |    "last": 0.01251541,
        |    "buy": 0.01251507,
        |    "sell": 0.01256931,
        |    "updated": 1530278143
        |  }
        |}
      """.stripMargin
    assert(YobitActor.parseTicker(Json.parse(test), "ltc_btc") === Some(BigDecimal("0.01251541")))
  }
}
