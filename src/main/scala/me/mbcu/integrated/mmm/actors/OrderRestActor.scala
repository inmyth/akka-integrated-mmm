package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorShutdown, Settings, ShutdownCode}
import me.mbcu.integrated.mmm.ops.common.AbsOrder.{CheckSafeForSeed, QueueRequest, SafeForSeed}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.scala.MyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OrderRestActor {

  case class GetLastCounter(book: ActorRef, bot: Bot, as: As)

  case class GotLastCounter(bc: BotCache, as: As)

  case class WriteLastCounter(book: ActorRef, bot: Bot, m: BotCache)

  case class LogActives(arriveMs: Long, buys: Seq[Offer], sels: Seq[Offer])
}

class OrderRestActor(bot: Bot, exchange: AbsExchange, fileActor: ActorRef) extends AbsOrder(bot) with MyLogging {
  private implicit val ec: ExecutionContextExecutor = global
  private var op: Option[ActorRef] = None
  private implicit val book: ActorRef = self
  private implicit val imBot: Bot = bot
  private var getFilledCancellable: Option[Cancellable] = None
  private var getActiveCancellable: Option[Cancellable] = None

  def receive: Receive = {

    case "start" =>
      op = Some(sender())
      fileActor ! GetLastCounter(self, bot, As.Init)

    case GotLastCounter(botCache, as) => qFilledOrders(Seq.empty[Offer], botCache.lastCounteredId, as) //must start from past filled orders. Starting bout qFilledOrders and qActiverOrders will mess up init orders

    case GotActiveOrders(offers, currentPage, nextPage, arriveMs, send) =>
      if (nextPage) {
        qActiveOrders(offers ++ send.cache, send.lastMs, currentPage + 1, send.as)
      }
      else {
        val activeOrders = send.cache ++ offers
        val (sortedBuys,sortedSels) = Offer.splitToBuysSels(activeOrders)
        send.as match {
          case As.Init =>
            bot.seed match {
              case a if a.equals(StartMethods.cont.toString) => // wont arrive here

              case a if a.equals(StartMethods.lastTicker.toString) =>
                qClearOrders(activeOrders, As.Init)
                queue1(GetTickerStartPrice(As.Init))

              case a if a.equals(StartMethods.lastOwn.toString) => op foreach(_ ! ErrorShutdown(ShutdownCode.fatal, -1, s"$a is not supported"))

              case _ =>
                qClearOrders(activeOrders, As.Init)
                qSeed(initialSeed(Seq.empty[Offer], Seq.empty[Offer], BigDecimal(bot.seed)))
                scheduleGetFilled(Settings.getFilledSeconds)

            }

          case As.RoutineCheck =>
            val dupes = AbsOrder.getDuplicates(sortedBuys) ++ AbsOrder.getDuplicates(sortedSels)
            val trims = if (bot.isStrictLevels) trim(sortedBuys, sortedSels, Side.buy) ++ trim(sortedBuys, sortedSels, Side.sell) else Seq.empty[Offer]
            val cancels = AbsOrder.margeTrimAndDupes(trims, dupes)
            qClearOrders(cancels._1, As.Trim)
            qClearOrders(cancels._2, As.KillDupes)
            qSeed(grow(sortedBuys, sortedSels, Side.buy) ++ grow(sortedBuys, sortedSels, Side.sell))

          case _ => // not handled
        }
        self ! LogActives(arriveMs, sortedBuys, sortedSels)
      }

    case GotUncounteredOrders(uncountereds, latestCounterId, isSortedFromOldest, arriveMs, send) =>
      fileActor ! WriteLastCounter(self, bot, BotCache(latestCounterId.getOrElse(send.lastCounterId)))
      def pipe(): Unit = {
        if (uncountereds.isEmpty) scheduleGetActive(1) else qCounter(uncountereds)
        scheduleGetFilled(Settings.getFilledSeconds) // should it be moved to GotActiveOrders$As.RoutineCheck ?
      }
      send.as match {
        case As.Init =>
          bot.seed match {
            case a if a.equals(StartMethods.cont.toString) => pipe()

            case _ => qActiveOrders(Seq.empty[Offer], System.currentTimeMillis(), page = 1, As.Init) // lastTicker or custom = config change, so past filled orders are not countered
          }

        case As.RoutineCheck => pipe()

        case _ => // not handled
      }


    case GotTickerStartPrice(price, arriveMs, send) => // start ownTicker
      price match {
        case Some(p) =>
          qSeed(initialSeed(Seq.empty[Offer], Seq.empty[Offer], p))
          scheduleGetFilled(Settings.getFilledSeconds)
        case _ => // no ticker price found
      }

    case SafeForSeed(yes) =>
      if (yes) {
        qActiveOrders(Seq.empty[Offer], System.currentTimeMillis(), page = 1, As.RoutineCheck)
      } else{
        scheduleGetActive(1)
      }

    case "get routine active orders" => op foreach(_! CheckSafeForSeed(self, bot))

    case "get filled orders" => fileActor ! GetLastCounter(self, bot, As.RoutineCheck)

    case LogActives(arriveMs, buys, sels) =>
      (arriveMs / 1000L) % 30 match {
        case x if x < 33 | x > 27 => info(Offer dump(bot, buys, sels))
        case _ => // ignore log
      }
  }

  def scheduleGetActive(s: Int) : Unit = {
    getActiveCancellable foreach(_.cancel())
    getActiveCancellable = Some(context.system.scheduler.scheduleOnce(s seconds, self, message="get routine active orders"))
  }

  def scheduleGetFilled(s: Int): Unit = {
    getFilledCancellable foreach(_.cancel())
    getFilledCancellable = Some(context.system.scheduler.scheduleOnce(s seconds, self, message="get filled orders"))
  }

  def qCounter(seq: Seq[Offer]): Unit = {
    val counters = Offer.sortTimeDesc(seq).map(counter).map(NewOrder(_, As.Counter))
    queue(counters)
  }

  def qSeed(offers: Seq[Offer]): Unit = queue(offers.map(NewOrder(_, As.Seed)))

  def qClearOrders(offers: Seq[Offer], as: As): Unit = queue(offers.map(CancelOrder(_,as)))

  def qActiveOrders(cache: Seq[Offer], lastMs: Long, page:Int, as: As) : Unit = queue1(GetActiveOrders(lastMs, cache, page, as))

  def qFilledOrders(cache: Seq[Offer], lastCounterId:String, as: As) : Unit = queue1(GetFilledOrders(lastCounterId, as))

  def queue(reqs: Seq[SendRest]): Unit = op foreach(_ ! QueueRequest(reqs))

  def queue1(req: SendRest): Unit = queue(Seq(req))

}
