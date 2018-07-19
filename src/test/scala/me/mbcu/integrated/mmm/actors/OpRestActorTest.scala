package me.mbcu.integrated.mmm.actors

import akka.actor.ActorRef
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.{Bot, Offer, Side}
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar

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

  test ("Set's uniqueness") {
    var set = scala.collection.mutable.Set[SendRequest]()
    val a = NewOrder(mock[Offer], Some(As.Counter))
    val b = NewOrder(mock[Offer], Some(As.Counter))
    val c = NewOrder(Offer.newOffer("abc", Side.buy, BigDecimal("100"), BigDecimal("100")), Some(As.Counter))
    set += a
    set += b
    set += c
    set -= a
    assert(!set.contains(a))

  }

}
