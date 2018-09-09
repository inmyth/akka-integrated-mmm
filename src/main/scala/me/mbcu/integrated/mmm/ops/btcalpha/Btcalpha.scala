package me.mbcu.integrated.mmm.ops.btcalpha

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.common.AbsExchange

object Btcalpha extends AbsExchange {

  override val name: Exchange = Exchange.btcalpha

  override val protocol: Protocol = Op.restgi

  override val endpoint: String = "https://btc-alpha.com/api/%s"

  override def intervalMillis: Int = 1000

  override def getActorRefProps: Props = Props(new BtcalphaActor())


}
