package me.mbcu.integrated.mmm.actors

import me.mbcu.integrated.mmm.ops.common.{Offer, Side, Status}
import org.scalatest.FunSuite

class OrderWsActorTest extends FunSuite{


  test(" match two maps for missing id"){
    val o = Seq(
      Offer("aaa", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("bbb", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("ccc", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("ddd", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("eee", "XRPUSD", Side.buy, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("fff", "XRPUSD", Side.buy, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("ggg", "XRPUSD", Side.buy, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("hhh", "XRPUSD", Side.buy, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None)
    )

    val p = Seq(
      Offer("ccc", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None),
      Offer("ddd", "XRPUSD", Side.sell, Status.active, 10L, None, BigDecimal(10), BigDecimal(1), None)
    )

    val x = o.map(a => a.id -> a).toMap
    val y = p.map(a => a.id -> a).toMap
    val res = x.collect{case a @ (_: String, _: Offer) if !y.contains(a._1) => a._2}.map(_.id).toSet
    assert(!res.contains("ccc"))
    assert(!res.contains("ddd"))
  }

}
