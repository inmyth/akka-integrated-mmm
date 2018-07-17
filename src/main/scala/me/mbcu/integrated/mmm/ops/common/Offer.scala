package me.mbcu.integrated.mmm.ops.common

import me.mbcu.integrated.mmm.ops.common
import me.mbcu.integrated.mmm.ops.common.Status.Status
import me.mbcu.integrated.mmm.ops.common.Side.Side
import play.api.libs.functional.syntax._
import play.api.libs.json._

object Side extends  Enumeration {
  type Side = Value
  val buy, sell = Value

  implicit val read = Reads.enumNameReads(Side)
  implicit val write = Writes.enumNameWrites

  def reverse(a : Side) : Side = {
    if (a == Side.buy) sell else buy
  }
}

object Status extends Enumeration {
  type Status = Value
  val filled: common.Status.Value = Value(2)
  val partialFilled: common.Status.Value = Value(1)
  val unfilled: common.Status.Value = Value(0)
  val cancelled: common.Status.Value = Value(-1)
  val cancelInProcess: common.Status.Value = Value(4)

  implicit val enumFormat = new Format[Status] {
    override def reads(json: JsValue): JsResult[Status] = json.validate[Int].map(Status(_))
    override def writes(enum: Status) = JsNumber(enum.id)
  }
}

object Offer {
  implicit val jsonFormat = Json.format[Offer]

  object Implicits {
    implicit val writes: Writes[Offer] = new Writes[Offer] {
      def writes(o: Offer): JsValue = Json.obj(
        "id" -> o.id,
        "symbol" -> o.symbol,
        "side" -> o.side,
        "status" -> o.status,
        "createdAt" -> o.createdAt,
        "updatedAt" -> o.updatedAt,
        "quantity" -> o.quantity,
        "price" -> o.price,
        "cumQuantity" -> o.cumQuantity
      )
    }

    implicit val reads: Reads[Offer] = (
      (JsPath \ "id").read[String] and
        (JsPath \ "symbol").read[String] and
        (JsPath \ "side").read[Side] and
        (JsPath \ "status").readNullable[Status] and
        (JsPath \ "createdAt").readNullable[Long] and
        (JsPath \ "updatedAt").readNullable[Long] and
        (JsPath \ "quantity").read[BigDecimal] and
        (JsPath \ "price").read[BigDecimal] and
        (JsPath \ "cumQuantity").readNullable[BigDecimal]
      ) (Offer.apply _)
  }

  def newOffer(symbol : String, side: Side, price : BigDecimal, quantity: BigDecimal) : Offer = Offer("unused", symbol, side, None, None, None, quantity, price, None)


  def dump(bot: Bot, sortedBuys: Seq[Offer], sortedSels: Seq[Offer]) : String = {
    val builder = StringBuilder.newBuilder
    builder.append(System.getProperty("line.separator"))
    builder.append(s"Open Orders ${bot.exchange}: ${bot.pair}")
    builder.append(System.getProperty("line.separator"))
    builder.append(s"buys : ${sortedBuys.size}")
    builder.append(System.getProperty("line.separator"))
    sortedBuys.foreach(b => {
      builder.append(s"id:${b.id} quantity:${b.quantity.bigDecimal.toPlainString} price:${b.price.bigDecimal.toPlainString} filled:${b.cumQuantity.get.bigDecimal.toPlainString}")
      builder.append(System.getProperty("line.separator"))
    })
    builder.append(s"sells : ${sortedSels.size}")
    builder.append(System.getProperty("line.separator"))
    sortedSels.foreach(s => {
      builder.append(s"id:${s.id} quantity:${s.quantity.bigDecimal.toPlainString} price:${s.price.bigDecimal.toPlainString} filled:${s.cumQuantity.get.bigDecimal.toPlainString}")
      builder.append(System.getProperty("line.separator"))
    })
    builder.toString()
  }

}

case class Offer (
                   id : String,
                   symbol: String,
                   side : Side,
                   status : Option[Status],
                   createdAt : Option[Long],
                   updatedAt : Option[Long],
                   quantity: BigDecimal,
                   price : BigDecimal,
                   cumQuantity : Option[BigDecimal]
                 )
