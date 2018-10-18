package me.mbcu.integrated.mmm

import java.util.TimeZone

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import me.mbcu.integrated.mmm.actors.BaseActor
import me.mbcu.scala.{MyLogging, MyLoggingSingle}

object Application extends App with MyLogging {
  implicit val system: ActorSystem = akka.actor.ActorSystem("mmm")
  implicit val materializer: ActorMaterializer = akka.stream.ActorMaterializer()

  if (args.length != 3){
    println("Requires two arguments : <config file path>  <log dir path> <last millis dir path>")
    System.exit(-1)
  }
  MyLoggingSingle.init(args(1), TimeZone.getTimeZone("Asia/Tokyo"))
  val mainActor = system.actorOf(Props(new BaseActor(args(0), args(2))), name = "main")
  mainActor ! "start"
}
