package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Props}
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.Messages._
import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

import me.mbcu.integrated.mmm.actors.FileActor.ConfigReady
import me.mbcu.integrated.mmm.ops.common.Config
import play.api.libs.json.{JsResult, Json}

import scala.util.Try

object FileActor {
  def props(path : String): Props = Props(new FileActor(path))

  case class ConfigReady(config: Try[Config])
}

class FileActor(path : String) extends Actor {
  private var parents: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" =>
      parents = Some(sender)
      val source = scala.io.Source.fromFile(path)
      val rawJson = try source.mkString finally source.close()
      val config: Try[Config] = Try(Json.parse(rawJson).as[Config])
      parents foreach (_ ! ConfigReady(config))

    case "listen" =>
      val fileMonitorActor = context.actorOf(MonitorActor(concurrency = 2))
      val modifyCallbackFile: Callback = { path => println(s"Something was modified in a file: $path")}
      val file = Paths get path
      /*
        This will receive callbacks for just the one file
      */
      fileMonitorActor ! RegisterCallback(
        event = ENTRY_MODIFY,
        path = file,
        callback =  modifyCallbackFile
      )

    case _ => println("FileActor#start: _")
  }

}