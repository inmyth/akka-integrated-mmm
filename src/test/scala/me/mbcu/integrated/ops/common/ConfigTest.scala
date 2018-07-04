package me.mbcu.integrated.ops.common

import me.mbcu.integrated.mmm.ops.Definitions
import me.mbcu.integrated.mmm.ops.common.Bot
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


}
