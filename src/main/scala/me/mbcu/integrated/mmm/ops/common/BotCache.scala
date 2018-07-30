package me.mbcu.integrated.mmm.ops.common

import play.api.libs.json._

object BotCache {
  def apply(latestCounterId: Option[String]): BotCache = {
    latestCounterId match {
      case Some(v) => BotCache(v)
      case _ => BotCache(defaultLastCounteredId)
    }
  }

  implicit val jsonFormat: OFormat[BotCache] = Json.format[BotCache]

  val defaultLastCounteredId: String = "-1"
  val default: BotCache = BotCache(defaultLastCounteredId)
}


case class BotCache(lastCounteredId: String)
