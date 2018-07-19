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
        |  "data": {
        |    "id": "xJ9b8hOdFu-0lkrBWv-LlOMq9ki697_8WwA9dt-tKfE=",
        |    "symbol": "ethusdt",
        |    "amount": "0.002000000000000000",
        |    "price": "475.190000000000000000",
        |    "created_at": 1531806296543,
        |    "type": "limit",
        |    "side": "sell",
        |    "filled_amount": "0.002000000000000000",
        |    "executed_value": "0.950380000000000000",
        |    "fill_fees": "0.000950380000000000",
        |    "source": "api",
        |    "state": "filled"
        |  }
        |}
      """.stripMargin

    val js = Json.parse(a)
    val data = (js \ "data").as[JsValue]
    val offer = FcoinActor.toOffer(data)
    assert(offer.status === Some(Status.filled))
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
    assert(offer.status === Some(Status.unfilled))

  }


}
