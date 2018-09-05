package me.mbcu.integrated.mmm.ops.yobit

import me.mbcu.integrated.mmm.ops.common.Side

object Sign extends App {


  val test = YobitRestRequest$.newOrder(args(0), "trx_eth", Side.buy, BigDecimal("0.00008137"), BigDecimal("10"))
  println(test.sign)
  println(test.params)

  val test2 = YobitRestRequest$.ownTrades(args(0), "trx_eth")
  println(test2.sign)
  println(test2.params)

  val test3 =YobitRestRequest$.infoOrder(args(0), "106374985614339")
  println(test3.sign)
  println(test3.params)

  val test4 = YobitRestRequest$.cancelOrder(args(0), "106374965614339")
  println(test4.sign)
  println(test4.params)

  val test5 = YobitRestRequest$.activeOrders(args(0), "trx_eth")
  println(test5.sign)
  println(test5.params)
}
