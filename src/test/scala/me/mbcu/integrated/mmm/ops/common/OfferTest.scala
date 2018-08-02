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


  test ("get duplicates from list with duplicates") {
    val symbol = "noah_rur"
    val active = Status.active
    val side = Side.sell // symbol, side, BigDecimal("0.07742766"),
    val offers = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098403259", symbol, side, active, 3L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098402116", symbol, side, active, 6L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098403227", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098402119", symbol, side, active, 8L, None, BigDecimal(2014), BigDecimal(".07582400"), None)
    )
    scala.util.Random.shuffle(offers)
    val res = Offer.getDuplicates(offers)
    assert(res.size === 2)
    assert(res.head.price === BigDecimal(".07582417"))
    assert(res.last.price === BigDecimal(".07635494"))

    val noDup = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098403227", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098402119", symbol, side, active, 8L, None, BigDecimal(2014), BigDecimal(".07582400"), None)
    )
    scala.util.Random.shuffle(noDup)
    val res2 = Offer.getDuplicates(noDup)
    assert(res2.size === 0)
  }


}
