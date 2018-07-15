package me.mbcu.integrated.ops.yobit

import me.mbcu.integrated.mmm.ops.yobit.YobitRequest
import org.scalatest.FunSuite

class YobitRequestTest extends FunSuite{
  val secret = "sknfskfw3904239sdf233242"

  test ("test Yobit sha512HMAC sign has to be in lowercase") {
    val test = YobitRequest.ownTrades(secret, "trx_eth")
    assert(test.sign.contains("a"))
    assert(!test.sign.contains("A"))
  }

}
