package me.mbcu.integrated.mmm.ops.common

import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Strategies.Strategies
import play.api.libs.functional.syntax._
import play.api.libs.json._


case class Credentials(pKey: String, nonce: String, signature: String)

object Credentials {
  implicit val jsonFormat: OFormat[Credentials] = Json.format[Credentials]
}

case class Env(emails: Option[Seq[String]], sesKey: Option[String], sesSecret: Option[String], logSeconds: Int)

object Env {
  implicit val jsonFormat: OFormat[Env] = Json.format[Env]

  object Implicits {
    implicit val envWrites: Writes[Env] {
      def writes(env: Env): JsValue
    } = new Writes[Env] {
      def writes(env: Env): JsValue = Json.obj(
        "emails" -> env.emails,
        "sesKey" -> env.sesKey,
        "sesSecret" -> env.sesSecret,
        "logSeconds" -> env.logSeconds
      )
    }

    implicit val envReads: Reads[Env] = (
      (JsPath \ "emails").readNullable[Seq[String]] and
        (JsPath \ "sesKey").readNullable[String] and
        (JsPath \ "sesSecret").readNullable[String] and
        (JsPath \ "logSeconds").read[Int]
      ) (Env.apply _)
  }

}


object StartingPrice extends Enumeration {
  type StartingPrice = Value
  val lastTicker, lastOwn, contAsIs = Value
}

case class Bot(
                exchange: Exchange,
                credentials: Credentials,
                pair: String,
                startingPrice : String,
                gridSpace: BigDecimal,
                buyGridLevels: Int,
                sellGridLevels: Int,
                buyOrderQuantity: BigDecimal,
                sellOrderQuantity: BigDecimal,
                quantityPower: Int,
                maxPrice: Option[BigDecimal],
                minPrice: Option[BigDecimal],
                counterScale: Int,
                baseScale: Int,
                isStrictLevels: Boolean,
                isNoQtyCutoff: Boolean,
                isHardReset: Boolean,
                strategy: Strategies
              )

object Bot {
  implicit val jsonFormat: OFormat[Bot] = Json.format[Bot]

  object Implicits {
    implicit val botWrites: Writes[Bot] {
      def writes(bot: Bot): JsValue
    } = new Writes[Bot] {
      def writes(bot: Bot): JsValue = Json.obj(
        "exchange" -> bot.exchange,
        "credentials" -> bot.credentials,
        "pair" -> bot.pair,
        "startingPrice" -> bot.startingPrice,
        "gridSpace" -> bot.gridSpace,
        "buyGridLevels" -> bot.buyGridLevels,
        "sellGridLevels" -> bot.sellGridLevels,
        "buyOrderQuantity" -> bot.buyOrderQuantity,
        "sellOrderQuantity" -> bot.sellOrderQuantity,
        "quantityPower" -> bot.quantityPower,
        "maxPrice" -> bot.maxPrice,
        "minPrice" -> bot.minPrice,
        "counterScale" -> bot.counterScale,
        "baseScale" -> bot.baseScale,
        "isStrictLevels" -> bot.isStrictLevels,
        "isNoQtyCutoff" -> bot.isNoQtyCutoff,
        "isHardReset" -> bot.isHardReset,
        "strategy" -> bot.strategy
      )
    }

    implicit val botReads: Reads[Bot] = (
      (JsPath \ "ops").read[Exchange] and
        (JsPath \ "credentials").read[Credentials] and
        (JsPath \ "pair").read[String] and
        (JsPath \ "startingPrice").read[String] and
        (JsPath \ "gridSpace").read[BigDecimal] and
        (JsPath \ "buyGridLevels").read[Int] and
        (JsPath \ "sellGridLevels").read[Int] and
        (JsPath \ "buyOrderQuantity").read[BigDecimal] and
        (JsPath \ "sellOrderQuantity").read[BigDecimal] and
        (JsPath \ "quantityPower").read[Int] and
        (JsPath \ "maxPrice").readNullable[BigDecimal] and
        (JsPath \ "minPrice").readNullable[BigDecimal] and
        (JsPath \ "counterScale").read[Int] and
        (JsPath \ "baseScale").read[Int] and
        (JsPath \ "isStrictLevels").read[Boolean] and
        (JsPath \ "isNoQtyCutoff").read[Boolean] and
        (JsPath \ "isHardReset").read[Boolean]
        and (JsPath \ "strategy").read[Strategies]
      ) (Bot.apply _)
  }

}

case class Config(env: Env, bots: List[Bot])

object Config {
  implicit val jsonFormat: OFormat[Config] = Json.format[Config]
}

