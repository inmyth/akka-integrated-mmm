package me.mbcu.integrated.mmm.ops.common

import akka.actor.{ActorRef, Props}
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Op.Protocol

abstract class AbsExchange {
  def name: Exchange

  def protocol : Protocol

  def endpoint : String

  def intervalMillis : Int

  def getActorRefProps: Props


}

trait AbsWsExchange {
   def getParser(op: ActorRef): AbsWsParser

   def getRequest: AbsWsRequest

   def orderId(offer: Offer): String
}
