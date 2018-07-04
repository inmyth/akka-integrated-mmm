package me.mbcu.integrated.ops.yobit.models

import me.mbcu.integrated.mmm.ops.yobit.models.YobitRequest
import org.scalatest.FunSuite

class YobitRequestTest extends FunSuite{
  val secret = "sknfskfw3904239sdf233242"

//  test("test yobit nonce gen non-idempotent") {
//    val nonce1 = YobitRequest.nonce
//    val nonce2 = YobitRequest.nonce
//    assert(nonce2 > nonce1)
//  }

  test ("test Yobit sha512HMAC sign has to be in lowercase") {
    val test = YobitRequest.ownTrades(secret, "trx_eth")
    assert(test.sign.contains("a"))
    assert(!test.sign.contains("A"))
  }


}
