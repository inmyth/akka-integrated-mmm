package me.mbcu.integrated.mmm.ops

import me.mbcu.integrated.mmm.ops.Definitions.ShutdownCode.ShutdownCode
import me.mbcu.integrated.mmm.ops.common.AbsExchange
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.SendRest
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


  case class ErrorIgnore(code: Int, msg: String, shouldEmail: Boolean)
  case class ErrorShutdown(shutdown: ShutdownCode, code: Int, msg: String)
  case class ErrorRetryRest(sendRequest: SendRest, code: Int, msg: String, shouldEmail: Boolean)


  object Settings {
    val cachingEmailSeconds:Int = 60
    val getActiveSeconds:Int = 5
    val getFilledSeconds:Int = 5
  }



}
