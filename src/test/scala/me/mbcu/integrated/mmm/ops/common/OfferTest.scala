package me.mbcu.integrated.mmm.ops.common

import me.mbcu.integrated.mmm.actors.OrderRestActor
import org.scalatest.FunSuite

class OfferTest extends FunSuite {


  test("sort created_at desc") {
    val o1 = Offer("a", "eth_noah", Side.buy, Status.debug, 1532579178L, None, BigDecimal("1000"), BigDecimal("0.00000031"), None)
    val o2 = Offer("a", "eth_noah", Side.buy, Status.active, 1532579278L, None, BigDecimal("1000"), BigDecimal("0.00000032"), None)
    val o3 = Offer("a", "eth_noah", Side.buy, Status.active, 1532579378L, None, BigDecimal("1000"), BigDecimal("0.00000033"), None)
    val o4 = Offer("a", "eth_noah", Side.buy, Status.active, 1532579478L, None, BigDecimal("1000"), BigDecimal("0.00000034"), None)
    val o5 = Offer("a", "eth_noah", Side.buy, Status.active, 1532679278L, None, BigDecimal("1000"), BigDecimal("0.00000035"), None)
    val a = Seq(o1, o2, o3, o4, o5)
    scala.util.Random.shuffle(a)
    val res = Offer.sortTimeDesc(a)
    assert(res.head.createdAt === 1532679278L)
    assert(res.last.createdAt === 1532579178L)
  }

  test("partition active orders into two sides") {
    val o1 = Offer("a", "eth_noah", Side.buy, Status.debug, 1532579178L, None, BigDecimal("1000"), BigDecimal("0.00000031"), None)
    val o2 = Offer("a", "eth_noah", Side.sell, Status.active, 1532579278L, None, BigDecimal("1000"), BigDecimal("0.00000032"), None)
    val o3 = Offer("a", "eth_noah", Side.buy, Status.active, 1532579378L, None, BigDecimal("1000"), BigDecimal("0.00000033"), None)
    val o4 = Offer("a", "eth_noah", Side.sell, Status.active, 1532579478L, None, BigDecimal("1000"), BigDecimal("0.00000034"), None)
    val o5 = Offer("a", "eth_noah", Side.buy, Status.active, 1532679278L, None, BigDecimal("1000"), BigDecimal("0.00000035"), None)
    val a = Seq(o1, o2, o3, o4, o5)
    scala.util.Random.shuffle(a)
    val (p,q) = Offer.splitToBuysSels(a)
    assert(p.head.side === Side.buy)
    assert(p.size === 3)
    assert(p.head.price > p(1).price)
    assert(p(1).price > p(2).price)
    assert(q.head.side === Side.sell)
    assert(q.size === 2)
    assert(q.head.price < q(1).price)
  }


  test("equality test") {
    val o1 = Offer("a", "eth_noah", Side.buy, Status.debug, 1532579178L, None, BigDecimal("1000"), BigDecimal("0.00000031"), None)
    val o2 = Offer("a", "eth_noah", Side.sell, Status.active, 1532579278L, None, BigDecimal("1000"), BigDecimal("0.00000032"), None)
    val o3 = Offer("b", "eth_noah", Side.buy, Status.active, 1532579378L, None, BigDecimal("1000"), BigDecimal("0.00000033"), None)

    assert(o1 === o2)
    assert(o1 !== o3)

    var set = scala.collection.mutable.Set[Offer]()
    set += o1
    set += o2
    set += o3
    assert(set.size === 2)
    assert(set.head.id === "b")
  }




}
