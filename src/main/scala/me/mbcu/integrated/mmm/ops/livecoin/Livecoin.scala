package me.mbcu.integrated.mmm.ops.livecoin

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.common.AbsExchange
import me.mbcu.integrated.mmm.ops.fcoin.FcoinActor

object Livecoin extends AbsExchange{

  override val name: Exchange = Exchange.livecoin

  override val protocol: Protocol = Op.rest

  override val endpoint: String = "https://api.livecoin.net/%s"

  override def intervalMillis: Int = 900

  override def getActorRefProps: Props = Props(new LivecoinActor())

}
