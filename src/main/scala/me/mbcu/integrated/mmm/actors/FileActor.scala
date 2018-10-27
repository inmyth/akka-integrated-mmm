package me.mbcu.integrated.mmm.actors

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorRef, Props}
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import me.mbcu.integrated.mmm.actors.BaseActor.ConfigReady
import me.mbcu.integrated.mmm.actors.FileActor.millisFileName
import me.mbcu.integrated.mmm.actors.OrderRestActor._
import me.mbcu.integrated.mmm.ops.common.{Bot, BotCache, Config}
import me.mbcu.scala.MyLogging
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

object FileActor extends MyLogging {
  def props(configPath: String, millisPath: String): Props = Props(new FileActor(configPath, millisPath))

  def readConfig(path: String): Option[Config] = {
    val source = scala.io.Source.fromFile(path)
    val rawJson = try source.mkString finally source.close()
    Try(Json.parse(rawJson).as[Config]) match {
      case Success(config) => Some(config)
      case Failure(e) =>
        error(e.getMessage)
        None
    }
  }


  def millisFileName(msPath: String, bot: Bot): String = msPath + Bot.millisFileFormat.format(bot.exchange.toString, Bot.filePath(bot))

  def readLastCounterId(path: String, bot: Bot): BotCache = {
    val fName = millisFileName(path, bot)
    if (!Files.exists(Paths.get(fName))) {
      val pw = new PrintWriter(new File(fName))
      pw.close()
    }
    val raw = readFile(fName)
    Try(Json.parse(raw).as[BotCache]) match {
      case Success(bc) => bc
      case Failure(e) =>
        e.getMessage match {
          case a if a contains "No content to map" => error("File is empty. Creating default.")
          case _ => error _
        }
        BotCache.default
    }
  }

  def readFile(path: String): String = {
    val source = scala.io.Source.fromFile(path)
    try source.mkString finally source.close()
  }

  def writeFile(path: String, content: String): Unit = Files.write(Paths.get(path), content.getBytes(StandardCharsets.UTF_8))

}

class FileActor(cfgPath: String, msPath: String) extends Actor {
  private var base: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" =>
      import FileActor._
      base = Some(sender)
      sender ! ConfigReady(readConfig(cfgPath))

    case "listen" =>
      val fileMonitorActor = context.actorOf(MonitorActor(concurrency = 2))
      val modifyCallbackFile: Callback = { path => println(s"Something was modified in a file: $path") }
      val file = Paths get cfgPath
      /*
        This will receive callbacks for just the one file
      */
      fileMonitorActor ! RegisterCallback(
        event = ENTRY_MODIFY,
        path = file,
        callback = modifyCallbackFile
      )

    case GetLastCounter(book, bot, as) => book ! GotLastCounter(FileActor.readLastCounterId(msPath, bot), as)

    case WriteLastCounter(book, bot, m) => FileActor.writeFile(millisFileName(msPath, bot), Json.toJson(m).toString())

  }


}