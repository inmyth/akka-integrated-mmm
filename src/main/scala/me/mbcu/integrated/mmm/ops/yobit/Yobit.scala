package me.mbcu.integrated.mmm.ops.yobit

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, Bot}

object Yobit extends AbsExchange {

  override val name: Exchange = Exchange.yobit

  override val protocol: Protocol = Op.restgi

  override val endpoint: String = "https://yobit.io/api/3/%s/%s"

  override val intervalMillis: Int = 2000

  val endpointTrade: String = "https://yobit.io/tapi/"

  override def getActorRefProps: Props = Props(new YobitActor())

  val nonceFactor : Long = 1530165626l // some random ts June 28

}
