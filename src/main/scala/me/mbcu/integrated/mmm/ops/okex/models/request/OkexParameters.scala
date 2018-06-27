package me.mbcu.integrated.mmm.ops.okex.models.request

import java.security.MessageDigest

import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.okex.models.request.OkexStatus.OkexStatus
import play.api.libs.functional.syntax._
import play.api.libs.json._

//object OkexType extends Enumeration {
//  type OkexType = Value
//  val buy, sell = Value
//
//  implicit val read = Reads.enumNameReads(OkexType)
//  implicit val write = Writes.enumNameWrites
//
//  def withNameOpt(s: String): Option[Value] = values.find(_.toString == s)
//}

object OkexStatus extends Enumeration {
  type OkexStatus = Value
  val filled = Value(1)
  val unfilled = Value(0)

  implicit val enumFormat = new Format[OkexStatus] {
    override def reads(json: JsValue): JsResult[OkexStatus] = json.validate[Int].map(OkexStatus(_))
    override def writes(enum: OkexStatus) = JsNumber(enum.id)
  }
}

//object EnumUtils {
//  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
//    def reads(json: JsValue): JsResult[E#Value] = json match {
//      case JsString(s) =>
//        try {
//          JsSuccess(enum.withName(s))
//        } catch {
//          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not appear to contain the value: '$s'")
//        }
//      case _ => JsError("String value expected")
//    }
//  }
//}

object OkexParameters {
  val md5: MessageDigest = MessageDigest.getInstance("MD5")

  implicit val jsonFormat = Json.format[OkexParameters]

  object Implicits {
    implicit val writes = new Writes[OkexParameters] {
      def writes(r: OkexParameters): JsValue = Json.obj(
        "sign" -> r.sign,
        "api_key" -> r.api_key,
        "symbol" -> r.symbol,
        "order_id" -> r.order_id,
        "type" -> r.symbol,
        "price" -> r.price,
        "amount" -> r.amount,
        "status" -> r.status,
        "current_page" -> r.current_page,
        "page_length" -> r.page_length
      )
    }

    implicit val reads: Reads[OkexParameters] = (
      (JsPath \ "sign").readNullable[String] and
      (JsPath \ "api_key").read[String] and
      (JsPath \ "symbol").readNullable[String] and
      (JsPath \ "order_id").readNullable[String] and
      (JsPath \ "type").readNullable[Side] and
      (JsPath \ "price").readNullable[BigDecimal] and
      (JsPath \ "amount").readNullable[BigDecimal] and
      (JsPath \ "status").readNullable[OkexStatus] and
      (JsPath \ "current_page").readNullable[Int] and
      (JsPath \ "page_length").readNullable[Int]
      ) (OkexParameters.apply _)
  }



}

case class OkexParameters (
    sign : Option[String],
    api_key : String,
    symbol : Option[String],
    order_id : Option[String],
    `type` : Option[Side],
    price : Option[BigDecimal],
    amount: Option[BigDecimal],
    status : Option[OkexStatus] = None,
    current_page : Option[Int] = None,
    page_length : Option[Int] = None


)
