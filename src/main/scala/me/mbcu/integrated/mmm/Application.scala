package me.mbcu.integrated.mmm

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.actors.BaseActor
import me.mbcu.integrated.mmm.utils.{MyLogging, MyLoggingSingle}

object Application extends App with MyLogging {



  implicit val system: ActorSystem = akka.actor.ActorSystem("mmm")
  implicit val materializer: ActorMaterializer = akka.stream.ActorMaterializer()

  if (args.length != 2){
    println("Requires two arguments : <config file path>  <log directory path>")
    System.exit(-1)
  }
  MyLoggingSingle.init(args(1))
//  info(s"START UP ${MyUtils.date()}")

  val mainActor = system.actorOf(Props(new BaseActor(args(0))), name = "main")
  mainActor ! "start"

}
