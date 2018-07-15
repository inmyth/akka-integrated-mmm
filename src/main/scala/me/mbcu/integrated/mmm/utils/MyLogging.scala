package me.mbcu.integrated.mmm.utils

trait MyLogging {

  def error(s : String): Unit = MyLoggingSingle.log.severe(s)

  def info(s :String) : Unit = MyLoggingSingle.log.info(s)

  def warn(s : String) : Unit = MyLoggingSingle.log.warning(s)

  def debug(s : String) : Unit = MyLoggingSingle.log.fine(s)
}