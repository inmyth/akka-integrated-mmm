package me.mbcu.integrated.mmm.ops.okex

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Protocol}
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}

object OkexRest extends AbsExchange {

  val name = Exchange.okexRest
  val protocol = Protocol.rest
  val endpoint = "https://www.okex.com/api/v1"
  override val intervalMillis: Int = 500

  override def getActorRefProps(bot: Bot): Props = Props(new OkexRestActor(bot))

  val OKEX_ERRORS = Map(
    1002 -> "The transaction amount exceed the balance",
    1003 -> "The transaction amount is less than the minimum",
    20100 -> "request time out",
    10005 -> "'SecretKey' does not exist. WARNING: This error can be caused by server error",
    10007 -> "Signature does not match. WARNING: This error can be caused by server error",
    10009 -> "Order does not exist",
    10010 -> "Insufficient funds",
    10011 -> "Amount too low",
    10014 -> "Order price must be between 0 and 1,000,000",
    10016 -> "Insufficient coins balance",
    10024 -> "balance not sufficient",
    -1000 -> "Server returns html garbage, most likely Cloudflare / SSL issues"
  )

}
