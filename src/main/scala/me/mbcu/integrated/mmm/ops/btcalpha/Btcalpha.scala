package me.mbcu.integrated.mmm.ops.btcalpha

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.common.AbsExchange
import me.mbcu.integrated.mmm.ops.livecoin.LivecoinActor

object Btcalpha extends AbsExchange {

  override val name: Exchange = Exchange.livecoin

  override val protocol: Protocol = Op.rest

  override val endpoint: String = "https://btc-alpha.com/api/v1/%s"

  override def intervalMillis: Int = 600

  override def getActorRefProps: Props = Props(new BtcalphaActor())
}
