package me.mbcu.integrated.mmm.ops.common

import akka.actor.{Actor, ActorRef}

abstract case class AbsOpActor(exchangeDef: AbsExchange, bots: Seq[Bot], fileActor: ActorRef) extends Actor
