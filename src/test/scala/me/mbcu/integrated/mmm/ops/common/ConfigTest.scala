package me.mbcu.integrated.mmm.ops.common

import me.mbcu.integrated.mmm.ops.Definitions
import me.mbcu.integrated.mmm.ops.Definitions.Exchange
import me.mbcu.integrated.mmm.ops.common.{Bot, Config}
import org.scalatest.FunSuite
import play.api.libs.json.Json

class ConfigTest extends FunSuite {

  test("Serialize Bot") {
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
    assert(b.exchange === Definitions.Exchange.okexRest)
    assert(b.credentials.signature === "super-secret")
  }

  test("Test bot grouping") {
    val a =
      """
        |{
        |     "env": {
        |         "emails" : ["martin.a@abc.com"],
        |         "sesKey" : "a",
        |         "sesSecret" : "b+YES31+HLdR",
        |         "logSeconds" : 10
        |     },
        |     "bots" : [
        |         {
        |             "exchange" : "fcoin",
        |             "credentials" : {
        |                 "pKey" : "aaa1",
        |                 "nonce" : "",
        |                 "signature": "bbb1"
        |             },
        |             "pair": "ethusdt",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |				 {
        |             "exchange" : "fcoin",
        |             "credentials" : {
        |                 "pKey" : "aaa2",
        |                 "nonce" : "",
        |                 "signature": "bbb2"
        |             },
        |             "pair": "ethusdt",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |					{
        |             "exchange" : "fcoin",
        |             "credentials" : {
        |                 "pKey" : "aaa2",
        |                 "nonce" : "",
        |                 "signature": "bbb2"
        |             },
        |             "pair": "ethbtc",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |				 {
        |             "exchange" : "okexRest",
        |             "credentials" : {
        |                 "pKey" : "aaa5",
        |                 "nonce" : "",
        |                 "signature": "bbb5"
        |             },
        |             "pair": "ethusdt",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |				 {
        |             "exchange" : "okexRest",
        |             "credentials" : {
        |                 "pKey" : "aaa6",
        |                 "nonce" : "",
        |                 "signature": "bbb6"
        |             },
        |             "pair": "ethbtc",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |				 {
        |             "exchange" : "okexRest",
        |             "credentials" : {
        |                 "pKey" : "aaa6",
        |                 "nonce" : "",
        |                 "signature": "bbb6"
        |             },
        |             "pair": "ethbtc",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         },
        |				 {
        |             "exchange" : "yobit",
        |             "credentials" : {
        |                 "pKey" : "aaa8",
        |                 "nonce" : "",
        |                 "signature": "bbb8"
        |             },
        |             "pair": "ethusdt",
        |             "seed" : "lastTicker",
        |             "gridSpace": "0.5",
        |             "buyGridLevels":0,
        |             "sellGridLevels": 3,
        |             "buyOrderQuantity": "0.002",
        |             "sellOrderQuantity": "0.002",
        |             "quantityPower" : 2,
        |             "counterScale" : 4,
        |             "baseScale" : 2,
        |             "isStrictLevels" : true,
        |             "isNoQtyCutoff" : true,
        |             "strategy" : "ppt"
        |         }
        |     ]
        | }
      """.stripMargin
    val b = Json.parse(a).as[Config]
    val res = Config.groupBots(b.bots)
    assert(b.bots.size === 7)
    assert(res(Exchange.fcoin).size === 2)
    assert(res(Exchange.okexRest).size === 2)
    assert(res(Exchange.yobit).size === 1)
  }


}
