package me.mbcu.integrated.mmm.ops.hitbtc

import akka.actor.{ActorRef, Props}
import me.mbcu.integrated.mmm.actors.WsActor.SendJs
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol
import me.mbcu.integrated.mmm.ops.Definitions.{Exchange, Op}
import me.mbcu.integrated.mmm.ops.common.AbsWsParser.SendWs
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.ops.yobit.YobitActor
import play.api.libs.json.{JsValue, Json}

object Hitbtc extends AbsExchange with AbsWsExchange {

  override val name: Exchange = Exchange.hitbtc

  override val protocol: Protocol = Op.ws

  override val endpoint: String = "wss://api.hitbtc.com/api/2/ws"

  override val intervalMillis: Int = 10

  override def getActorRefProps: Props = Props.empty

//  override def login(credentials: Credentials): SendWs = HitbtcRequest.login(credentials)

  override def getParser(op: ActorRef): AbsWsParser = new HitbtcParser(op)

  override val getRequest: AbsWsRequest = HitbtcRequest

  override def orderId(offer: Offer): String = HitbtcRequest.orderId(offer)

//  override def subscribe: SendWs = HitbtcRequest.subscribe


}
