package me.mbcu.integrated.mmm.ops.hitbtc

import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As
import me.mbcu.integrated.mmm.ops.common.{Offer, Side}
import org.scalatest.FunSuite
import play.api.libs.json.Json

class HitbtcRequestTest extends FunSuite{

  test("cancel order") {
    val js = HitbtcRequest.cancelOrder("aaa", As.Trim).jsValue
    val res = Json.parse(js.toString())
    assert((res \ "params" \ "clientOrderId").as[String] === "aaa")
    assert((res \ "id").as[String] === "cancelOrder.aaa")
  }

  test("clientOrderId length") {

    val pair1 = "PREMINEPREMINE"

    val o1 = Offer.newOffer(pair1, Side.sell, BigDecimal(100), BigDecimal(100))
    val res1=  HitbtcRequest.orderId(o1)
    assert(res1.length === 32)

    val pair2 = "NOAHETH"
    val o2 = Offer.newOffer(pair2, Side.sell, BigDecimal(100), BigDecimal(100))

    val res2=  HitbtcRequest.orderId(o2)
    assert(res2.length === 32)
  }

  test ("clientOrderId from id") {
    val pair = "NOAHETH"
    val method = "newOrder"
    val o = Offer.newOffer(pair, Side.sell, BigDecimal(100), BigDecimal(100))

    val clientOrderId =  HitbtcRequest.orderId(o)
    val id = HitbtcRequest.requestId(clientOrderId, method)
    val res = HitbtcRequest.clientOrderIdFrom(id)
    assert(res === clientOrderId)
  }


}
