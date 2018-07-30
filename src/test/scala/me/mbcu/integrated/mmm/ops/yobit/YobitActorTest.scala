package me.mbcu.integrated.mmm.ops.yobit

import me.mbcu.integrated.mmm.ops.common.{BotCache, Offer, Side, Status}
import org.scalatest.FunSuite
import play.api.libs.json.{JsObject, Json}

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

    val js = Json.parse(test)
    val data = YobitActor.parseOrders(js, YobitActor.filledToOffer)
    assert(data.head.price === BigDecimal("0.00008871"))

  }

  test ("test parse filled") {
    val a =
      """
        |{
        |  "success": 1,
        |  "return": {
        |    "200038780": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2970,
        |      "rate": 0.00000299,
        |      "order_id": "2073821086730612",
        |      "is_your_order": 1,
        |      "timestamp": "1532863442"
        |    },
        |    "200038779": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2985,
        |      "rate": 0.00000297,
        |      "order_id": "2073821086730586",
        |      "is_your_order": 1,
        |      "timestamp": "1532863318"
        |    },
        |    "200038766": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2970,
        |      "rate": 0.00000325,
        |      "order_id": "2073821085728446",
        |      "is_your_order": 1,
        |      "timestamp": "1532856533"
        |    },
        |    "200038752": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 138.48361334,
        |      "rate": 0.00000317,
        |      "order_id": "1073821085728402",
        |      "is_your_order": 1,
        |      "timestamp": "1532851429"
        |    },
        |    "200038751": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 2892.51638666,
        |      "rate": 0.00000317,
        |      "order_id": "1073821085728402",
        |      "is_your_order": 1,
        |      "timestamp": "1532851417"
        |    },
        |    "200038750": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 3015,
        |      "rate": 0.00000319,
        |      "order_id": "1073821085728383",
        |      "is_your_order": 1,
        |      "timestamp": "1532851400"
        |    },
        |    "200038746": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2985,
        |      "rate": 0.00000323,
        |      "order_id": "2073821085728564",
        |      "is_your_order": 1,
        |      "timestamp": "1532851283"
        |    },
        |    "200038730": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 170.32578948,
        |      "rate": 0.00000348,
        |      "order_id": "1073821085662180",
        |      "is_your_order": 1,
        |      "timestamp": "1532835691"
        |    },
        |    "200038729": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 2844.67421052,
        |      "rate": 0.00000348,
        |      "order_id": "1073821085662180",
        |      "is_your_order": 1,
        |      "timestamp": "1532835682"
        |    },
        |    "200038712": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2985,
        |      "rate": 0.00000317,
        |      "order_id": "2073821084587151",
        |      "is_your_order": 1,
        |      "timestamp": "1532810647"
        |    },
        |    "200038707": {
        |      "pair": "noah_eth",
        |      "type": "buy",
        |      "amount": 3015,
        |      "rate": 0.00000334,
        |      "order_id": "1073821084371554",
        |      "is_your_order": 1,
        |      "timestamp": "1532805475"
        |    },
        |    "200038702": {
        |      "pair": "noah_eth",
        |      "type": "sell",
        |      "amount": 2985,
        |      "rate": 0.00000334,
        |      "order_id": "2073821084115711",
        |      "is_your_order": 1,
        |      "timestamp": "1532800462"
        |    }
        |  }
        |}
      """.stripMargin
      val js = Json.parse(a)
      val bc1 = BotCache("200038746")
      val bc1res = YobitActor.parseFilled(js, bc1.lastCounteredId.toLong)
      assert(bc1res._2 === Some("200038780"))
      assert(bc1res._1.size === 5) // raw size is 6 but because of id grouping it becomes 5
      assert(bc1res._1.head.createdAt < bc1res._1(1).createdAt) // sorted from oldest
      assert(bc1res._1(1).createdAt < bc1res._1(2).createdAt)
      assert(bc1res._1(2).createdAt < bc1res._1(3).createdAt)
      assert(bc1res._1(3).createdAt < bc1res._1(4).createdAt)

      val bc2 = BotCache.default
      val bc2res = YobitActor.parseFilled(js, bc2.lastCounteredId.toLong)
      assert(bc2res._2 === Some("200038780"))
      assert(bc2res._1.size === 10)
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

    val js = Json.parse(test)
    val res = YobitActor.parseOrders(js, YobitActor.activeToOffer)
    assert(res.head.id === "106374965790953")
    assert(res.head.status === Status.active)
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
