package me.mbcu.integrated.mmm.actors

import akka.actor.ActorRef
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{Bot, Offer, Side}
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json

import scala.collection.mutable

class OpRestActorTest extends FunSuite with MockitoSugar {

  implicit val bot = mock[Bot]
  implicit val actorRef = mock[ActorRef]

  test ("remove existing GetOrderInfo from queue if same requests are already pending") {
    val a = Seq(GetOpenOrderInfo("aaa", None), CancelOrder("zzz", None), GetOpenOrderInfo("ccc", None))
    val batch = Seq(GetOpenOrderInfo("aaa", None), GetOpenOrderInfo("bbb", None))

    val prep = a.collect {
      case b: GetOpenOrderInfo => b
    }.map(_.id)
    val res = batch.filter(d => !prep.contains(d.id))
    assert(res.map(_.id).contains("bbb"))
    assert(!res.map(_.id).contains("aaa"))
  }


  test("check isNotInQueue") {
    val q : mutable.Queue[SendRequest]= mutable.Queue(NewOrder(mock[Offer], Some(As.Counter)))
    val nos = mutable.Set(NewOrder(mock[Offer], Some(As.Counter)), NewOrder(mock[Offer], Some(As.Counter)))
    val ass = Seq(As.Seed, As.Counter, As.ClearOpenOrders)

    val a = """        {
              |            "exchange" : "okexRest",
              |            "credentials" : {
              |                "pKey" : "api-key",
              |                "nonce" : "no-nce",
              |                "signature": "super-secret"
              |            },
              |            "pair": "trx_eth",
              |            "seed" : "lastOwn",
              |            "gridSpace": "0.2",
              |            "buyGridLevels": 5,
              |            "sellGridLevels": 5,
              |            "buyOrderQuantity": "20",
              |            "sellOrderQuantity": "20",
              |            "quantityPower" : 2,
              |            "counterScale" : 2,
              |            "baseScale" : 8,
              |            "isStrictLevels" : true,
              |            "isNoQtyCutoff" : true,
              |            "isHardReset" : true,
              |            "strategy" : "ppt"
              |        }""".stripMargin

    val b = Json.parse(a).as[Bot]

    val res = OpRestActor.isNotInQueue(q, nos, b, ass)
    println(res)



  }
}
