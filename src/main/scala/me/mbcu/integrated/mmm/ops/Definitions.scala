package me.mbcu.integrated.mmm.ops

import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsExchange
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.SendRequest
import me.mbcu.integrated.mmm.ops.fcoin.Fcoin
import me.mbcu.integrated.mmm.ops.okex.OkexRest
import me.mbcu.integrated.mmm.ops.yobit.Yobit
import me.mbcu.integrated.mmm.sequences.Strategy
import play.api.libs.json.{Reads, Writes}

import scala.language.implicitConversions

object Definitions {

  val exchangeMap = Map[Exchange.Value, AbsExchange](
    Exchange.okexRest -> OkexRest,
    Exchange.yobit -> Yobit,
    Exchange.fcoin -> Fcoin
  )

  object ShutdownCode extends Enumeration {
    type ShutdownCode = Value
    val fatal = Value(-1) // auth, login
    val recover = Value(1) // server error
  }

  object Exchange extends Enumeration {
    type Exchange = Value
    val okexRest, yobit, fcoin = Value

    implicit val reads = Reads.enumNameReads(Exchange)
    implicit val writes = Writes.enumNameWrites

  }

  object Protocol extends Enumeration {
    type Protocol = Value
    val rest, ws = Value
  }

  object Strategies extends Enumeration {
    type Strategies = Value
    val ppt, fullfixed = Value

    implicit val reads = Reads.enumNameReads(Strategies)
    implicit val writes = Writes.enumNameWrites
  }


  case class ErrorIgnore(code: Int, msg: String)
  case class ErrorShutdown(shutdown: ShutdownCode, code: Int, msg: String)
  case class ErrorRetryRest(sendRequest: SendRequest, code: Int, msg: String)


  object Settings extends Enumeration{
    type Settings = Value
    val cachingEmailSeconds = Value(60)
    val orderbookLogSeconds = Value(30)
    val int500 = Value(500)
    val int1 = Value(1)
    val int5 = Value(5)

  }



}
