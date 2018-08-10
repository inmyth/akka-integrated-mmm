package me.mbcu.integrated.mmm.ops.ddex

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.common.AbsExchange

object Ddex extends AbsExchange {
  override def name: Exchange = Exchange.ddex

  override def protocol: Protocol = Op.ddex

  override def endpoint: String = "https://api.ddex.io/v2/%s"

  override def intervalMillis: Int = 10

  override def getActorRefProps: Props = Props(new DdexActor())

}
