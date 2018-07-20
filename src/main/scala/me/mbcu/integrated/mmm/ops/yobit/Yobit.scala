package me.mbcu.integrated.mmm.ops.yobit

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Protocol.Protocol
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Protocol}
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}

object Yobit extends AbsExchange {

  override val name: Exchange = Exchange.yobit

  override val protocol: Protocol = Protocol.rest

  override val endpoint: String = "https://yobit.net/api/3/%s/%s"

  override val intervalMillis: Int = 800

  val endpointTrade: String = "https://yobit.net/tapi/"

  override def getActorRefProps: Props = Props(new YobitActor())

  val nonceFactor : Long = 1530165626000l // some random ts June 28

  override val seedIfEmpty: Boolean = true
}
