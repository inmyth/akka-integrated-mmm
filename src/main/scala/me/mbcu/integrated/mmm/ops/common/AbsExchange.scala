package me.mbcu.integrated.mmm.ops.common

import akka.actor.Props
import me.mbcu.integrated.mmm.ops.Definitions.Exchange.Exchange
import me.mbcu.integrated.mmm.ops.Definitions.Protocol.Protocol

abstract class AbsExchange {
  def name: Exchange

  def protocol : Protocol

  def endpoint : String

  def intervalMillis : Int

  def getActorRefProps: Props

}
