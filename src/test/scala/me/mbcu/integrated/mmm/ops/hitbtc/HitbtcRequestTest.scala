package me.mbcu.integrated.mmm.ops.hitbtc

import java.util.Date

import me.mbcu.integrated.mmm.ops.common.Side
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.FunSuite
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer

class HitbtcRequestTest extends FunSuite{

  test("cancel order") {
    val js = HitbtcRequest.cancelOrder("aaa").jsValue
    val res = Json.parse(js.toString())
    assert((res \ "params" \ "clientOrderId").as[String] === "aaa")
    assert((res \ "id").as[String] === "cancelOrder.aaa")
  }

  test("clientOrderId length") {
    val pair1 = "PREMINEPREMINE"
    val res1=  HitbtcRequest.clientOrderId(pair1, Side.sell)
    assert(res1.length === 32)

    val pair2 = "NOAHETH"
    val res2=  HitbtcRequest.clientOrderId(pair2, Side.sell)
    assert(res2.length === 32)
  }

  test ("clientOrderId from id") {
    val pair = "NOAHETH"
    val method = "newOrder"
    val clientOrderId =  HitbtcRequest.clientOrderId(pair, Side.sell)
    val id = HitbtcRequest.requestId(pair, method, clientOrderId)
    val res = HitbtcRequest.clientOrderIdFrom(id)
    assert(res === clientOrderId)
  }


}
