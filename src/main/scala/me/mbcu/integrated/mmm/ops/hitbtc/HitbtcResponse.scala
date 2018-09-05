package me.mbcu.integrated.mmm.ops.hitbtc

import play.api.libs.json.{Json, OFormat}

object RPCError {
  implicit val jsonFormat: OFormat[RPCError] = Json.format[RPCError]
}
case class RPCError (code : Int, message : Option[String], description : Option[String])

class HitbtcResponse {

}
