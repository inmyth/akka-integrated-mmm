package me.mbcu.integrated.mmm.utils

class MyLoggingSingle(logPath : String) {}

object MyLoggingSingle {
  import java.util.Date
  import java.util.logging.{Level, Logger}
  import java.text.SimpleDateFormat
  import java.util.TimeZone
  import java.util.logging.{FileHandler, SimpleFormatter}

  private val FILE_NAME = "log.%s.txt"
  private val limit = 1024 * 1024 * 20 // 20 Mb
  private val numLogFiles = 20
  private val sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SS")

  var log : Logger = _

  def init(logPath : String) : Unit = {
    log = Logger.getLogger("")
    log.setLevel(Level.FINE)
    val timeStamp = sdf.format(new Date())
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"))
    val fileName = String.format(logPath + FILE_NAME, timeStamp)
    val fileHandler = new FileHandler(fileName, limit, numLogFiles, true)
    // Create txt Formatter
    val formatter = new SimpleFormatter
    fileHandler.setFormatter(formatter)
    log.addHandler(fileHandler)
    //    val consoleHandler = new ConsoleHandler
    //    consoleHandler.setLevel(Level.ALL)
    //    consoleHandler.setFormatter(formatter)
    //    logger.addHandler(consoleHandler)

  }

}