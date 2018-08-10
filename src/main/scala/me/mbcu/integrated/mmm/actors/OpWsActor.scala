package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef}
import me.mbcu.integrated.mmm.ops.common.{AbsExchange, AbsOpActor, Bot}

class OpWsActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends AbsOpActor(exchangeDef, bots, fileActor) {

  override def receive: Receive = {

    case "start" =>
  }
}
