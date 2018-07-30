package me.mbcu.integrated.mmm.ops.okex

import me.mbcu.integrated.mmm.ops.common.Status
import org.scalatest.FunSuite
import play.api.libs.json.{JsObject, Json}

class OkexRestActorTest extends FunSuite{

  test("parse filled") {
    val a =
      """
        |{
        |  "result": true,
        |  "total": 5,
        |  "currency_page": 1,
        |  "page_length": 200,
        |  "orders": [
        |    {
        |      "amount": 1E+1,
        |      "avg_price": 0.00008081,
        |      "create_date": 1532873222000,
        |      "deal_amount": 1E+1,
        |      "order_id": 40611446,
        |      "orders_id": 40611446,
        |      "price": 0.00008081,
        |      "status": 2,
        |      "symbol": "trx_eth",
        |      "type": "sell"
        |    },
        |    {
        |      "amount": 1E+1,
        |      "avg_price": 0,
        |      "create_date": 1532873073000,
        |      "deal_amount": 0,
        |      "order_id": 40610842,
        |      "orders_id": 40610842,
        |      "price": 0.00008117,
        |      "status": -1,
        |      "symbol": "trx_eth",
        |      "type": "sell"
        |    },
        |    {
        |      "amount": 1E+1,
        |      "avg_price": 0,
        |      "create_date": 1532872788000,
        |      "deal_amount": 0,
        |      "order_id": 40609669,
        |      "orders_id": 40609669,
        |      "price": 0.00008118,
        |      "status": -1,
        |      "symbol": "trx_eth",
        |      "type": "sell"
        |    },
        |    {
        |      "amount": 1E+1,
        |      "avg_price": 0.00008090,
        |      "create_date": 1532867632000,
        |      "deal_amount": 1E+1,
        |      "order_id": 40586533,
        |      "orders_id": 40586533,
        |      "price": 0.0000809,
        |      "status": 2,
        |      "symbol": "trx_eth",
        |      "type": "sell"
        |    },
        |    {
        |      "amount": 1E+1,
        |      "avg_price": 0.00008057,
        |      "create_date": 1532865557000,
        |      "deal_amount": 1E+1,
        |      "order_id": 40575742,
        |      "orders_id": 40575742,
        |      "price": 0.00008057,
        |      "status": 2,
        |      "symbol": "trx_eth",
        |      "type": "sell"
        |    }
        |  ]
        |}
      """.stripMargin

    val res = OkexRestActor.parseFilled(Json.parse(a), 40575742)
    assert(res._1.size === 2)
    assert(res._1.head.id.toLong < res._1(1).id.toLong)


  }

}
