package me.mbcu.integrated.mmm.ops.fcoin

import me.mbcu.integrated.mmm.ops.common.Status
import org.scalatest.FunSuite
import play.api.libs.json.{JsValue, Json}

class FcoinActorTest extends  FunSuite{

  test("parse response to offer (filled") {
    val a =
      """
        |{
        |  "status": 0,
        |  "data": [
        |    {
        |      "id": "h_tbQC5CJIbBeCgtIq3f3KUP_3ODRt6mlw4W9m7MwVg=",
        |      "symbol": "xrpusdt",
        |      "amount": "3.040000000000000000",
        |      "price": "0.440000000000000000",
        |      "created_at": 1532066585373,
        |      "type": "limit",
        |      "side": "buy",
        |      "filled_amount": "3.040000000000000000",
        |      "executed_value": "1.337600000000000000",
        |      "fill_fees": "0.003040000000000000",
        |      "source": "api",
        |      "state": "filled"
        |    },
        |    {
        |      "id": "IUDiFCdkTPatOu0Lpu5TD3fRVMD_nYokzgrY2ggiRZY=",
        |      "symbol": "xrpusdt",
        |      "amount": "3.020000000000000000",
        |      "price": "0.450000000000000000",
        |      "created_at": 1532066584567,
        |      "type": "limit",
        |      "side": "buy",
        |      "filled_amount": "3.020000000000000000",
        |      "executed_value": "1.359000000000000000",
        |      "fill_fees": "0.003020000000000000",
        |      "source": "api",
        |      "state": "filled"
        |    },
        |    {
        |      "id": "UfqKwFqGPaIQax6xMml0G-oUdY19AobGxmJNKXfk4Z8=",
        |      "symbol": "xrpusdt",
        |      "amount": "14.920000000000000000",
        |      "price": "0.478400000000000000",
        |      "created_at": 1531844657648,
        |      "type": "limit",
        |      "side": "sell",
        |      "filled_amount": "14.920000000000000000",
        |      "executed_value": "7.137728000000000000",
        |      "fill_fees": "0.007137728000000000",
        |      "source": "api",
        |      "state": "filled"
        |    },
        |    {
        |      "id": "NO5qUaGqzppONSEmlor3iYiUPhTYNgko3zsLCVdjFAk=",
        |      "symbol": "xrpusdt",
        |      "amount": "15.080000000000000000",
        |      "price": "0.473600000000000000",
        |      "created_at": 1531844368429,
        |      "type": "limit",
        |      "side": "buy",
        |      "filled_amount": "15.080000000000000000",
        |      "executed_value": "7.141888000000000000",
        |      "fill_fees": "0.015080000000000000",
        |      "source": "api",
        |      "state": "filled"
        |    },
        |    {
        |      "id": "RcHxUSl3u3jCdBcnH196hzUgYpEc2xV5i-_XYXxgfCk=",
        |      "symbol": "xrpusdt",
        |      "amount": "300.000000000000000000",
        |      "price": "0.463200000000000000",
        |      "created_at": 1531751435077,
        |      "type": "limit",
        |      "side": "sell",
        |      "filled_amount": "300.000000000000000000",
        |      "executed_value": "138.960000000000000000",
        |      "fill_fees": "0.138960000000000000",
        |      "source": "web",
        |      "state": "filled"
        |    }
        |  ]
        |}
      """.stripMargin


    val data = Json.parse(a) \ "data"
    val res1 = FcoinActor.parseFilled(data, "NO5qUaGqzppONSEmlor3iYiUPhTYNgko3zsLCVdjFAk=")
    assert(res1._1.size === 3)
    assert(res1._2 === Some("h_tbQC5CJIbBeCgtIq3f3KUP_3ODRt6mlw4W9m7MwVg="))

    val res2 = FcoinActor.parseFilled(data, "id not exist")  // all offers are uncountered
    assert(res2._1.size === 5)
  }

  test( "parse response to offer (submitted)") {
    val a =
      """
        |{
        |  "status": 0,
        |  "data": {
        |    "id": "YnkBPuViqnLSJMh5p2t3wAqJ042mZOt93A2TVUCSqIs=",
        |    "symbol": "ethusdt",
        |    "amount": "0.001900000000000000",
        |    "price": "477.570000000000000000",
        |    "created_at": 1531803578106,
        |    "type": "limit",
        |    "side": "sell",
        |    "filled_amount": "0.000000000000000000",
        |    "executed_value": "0.000000000000000000",
        |    "fill_fees": "0.000000000000000000",
        |    "source": "api",
        |    "state": "submitted"
        |  }
        |}
      """.stripMargin

    val js = Json.parse(a)
    val data = (js \ "data").as[JsValue]
    val offer = FcoinActor.toOffer(data)
    assert(offer.status === Status.active)

  }


}
