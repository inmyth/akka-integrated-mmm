package me.mbcu.integrated.mmm.utils


import scala.util.{Failure, Success, Try}

object MyUtils {

  def parseToBigDecimal(s: String): Option[BigDecimal] = {
    Try(BigDecimal(s)) match {
      case Success(r) => Some(r)
      case Failure(e) => None
     }
  }
}
