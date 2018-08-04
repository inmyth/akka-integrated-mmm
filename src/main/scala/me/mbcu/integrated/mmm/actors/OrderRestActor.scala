package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderRestActor._
import me.mbcu.integrated.mmm.actors.OrderbookRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.Settings
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.sequences.Strategy
import me.mbcu.integrated.mmm.sequences.Strategy.PingPong
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OrderRestActor {

  case class GetLastCounter(book: ActorRef, bot: Bot, as: As)

  case class GotLastCounter(bc: BotCache, as: As)

  case class WriteLastCounter(book: ActorRef, bot: Bot, m: BotCache)

  case class CheckSafeForSeed(ref: ActorRef, bot: Bot)

  case class SafeForSeed(res: Boolean)

  case class LogActives(arriveMs: Long, buys: Seq[Offer], sels: Seq[Offer])
}

class OrderRestActor(bot: Bot, exchange: AbsExchange, fileActor: ActorRef) extends Actor with MyLogging {
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

    case GotLastCounter(botCache, as) => qFilledOrders(Seq.empty[Offer], botCache.lastCounteredId, as)

    case GotActiveOrders(offers, currentPage, nextPage, arriveMs, send) =>
      if (nextPage) {
        qActiveOrders(offers ++ send.cache, send.lastMs, currentPage + 1, send.as)
      }
      else {
        val activeOrders = send.cache ++ offers
        val (buys,sels) = Offer.splitToBuysSels(activeOrders)
        send.as match {
          case As.Init =>
            bot.seed match {
              case a if a.equals(StartMethods.cont.toString) => // ignore
              case a if a.equals(StartMethods.lastTicker.toString) =>
                qClearOrders(activeOrders, As.Init)
                queue1(GetTickerStartPrice(As.Init))
              case _ =>
                qClearOrders(activeOrders, As.Init)
                val seed = initialSeed(Seq.empty[Offer], Seq.empty[Offer], BigDecimal(bot.seed))
                qSeed(seed)
            }
          case As.RoutineCheck =>
            val (dupBuys, dupSels) = (Offer.getDuplicates(buys), Offer.getDuplicates(sels))
            (dupBuys.size, dupSels.size) match {
              case (0,0) =>
                qSeed(grow(buys, sels, Side.buy) ++ grow(buys, sels, Side.sell))
                if(bot.isStrictLevels) qClearOrders(trim(buys, sels, Side.sell) ++ trim(buys, sels, Side.buy), As.Trim)
              case _ => qClearOrders(dupBuys ++ dupSels, As.KillDupes)
            }

          case _ => // not handled
        }
        self ! LogActives(arriveMs, buys, sels)
      }

    case GotUncounteredOrders(uncountereds, latestCounterId, isSortedFromOldest, arriveMs, send) =>
      fileActor ! WriteLastCounter(self, bot, BotCache(latestCounterId))
      send.as match {
        case As.Init =>
          bot.seed match {
            case a if a.equals(StartMethods.cont.toString) =>
              qCounter(uncountereds)
              scheduleGetFilled(Settings.getFilledSeconds)
            case _ => qActiveOrders(Seq.empty[Offer], System.currentTimeMillis(), page = 1, As.Init)
          }
        case As.RoutineCheck =>
          qCounter(uncountereds)
          scheduleGetFilled(Settings.getFilledSeconds)

        case _ => // not handled
      }
      if (uncountereds.isEmpty) self ! "get active orders"

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

    case "get active orders" => op foreach(_! CheckSafeForSeed(self, bot))

    case "get filled orders" => fileActor ! GetLastCounter(self, bot, As.RoutineCheck)

    case LogActives(arriveMs, buys, sels) =>
      (arriveMs / 1000L) % 30 match {
      case x if x < 33 | x > 27 => info(Offer dump(bot, buys, sels))
      case _ => // ignore log
    }
  }

  def scheduleGetActive(s: Int) : Unit = {
    getActiveCancellable foreach(_.cancel())
    getActiveCancellable = Some(context.system.scheduler.scheduleOnce(s seconds, self, message="get active orders"))
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

  def qClearOrders(offers: Seq[Offer], as: As): Unit = queue(offers.map(_.id).map(CancelOrder(_,as)))

  def qActiveOrders(cache: Seq[Offer], lastMs: Long, page:Int, as: As) : Unit = queue1(GetActiveOrders(lastMs, cache, page, as))

  def qFilledOrders(cache: Seq[Offer], lastCounterId:String, as: As) : Unit = queue1(GetFilledOrders(lastCounterId, as))

  def queue(reqs: Seq[SendRest]): Unit = op foreach(_ ! QueueRequest(reqs))

  def queue1(req: SendRest): Unit = queue(Seq(req))

  def initialSeed(sortedBuys: Seq[Offer], sortedSels: Seq[Offer], midPrice: BigDecimal): Seq[Offer] = {
    var res: Seq[Offer] = Seq.empty[Offer]
    var buyQty = BigDecimal(0)
    var selQty = BigDecimal(0)
    var calcMidPrice = midPrice
    var buyLevels = 0
    var selLevels = 0

    def set(up: BigDecimal, bq: BigDecimal, sq: BigDecimal, bl: Int, sl: Int): Unit = {
      buyQty = bq
      selQty = sq
      buyLevels = bl
      selLevels = sl
      calcMidPrice = up
    }

    (sortedBuys.size, sortedSels.size) match {
      case (a, s) if a == 0 && s == 0 => set(midPrice, bot.buyOrderQuantity, bot.sellOrderQuantity, bot.buyGridLevels, bot.sellGridLevels)

      case (a, s) if a != 0 && s == 0 =>
        val anyBuy = sortedBuys.head
        val calcMid = Strategy.calcMid(anyBuy.price, anyBuy.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, bot.baseScale, Side.buy, midPrice, bot.strategy)
        val bl = calcMid._3 - 1
        set(calcMid._1, calcMid._2, calcMid._2, bl, bot.sellGridLevels)

      case (a, s) if a == 0 && s != 0 =>
        val anySel = sortedSels.head
        val calcMid = Strategy.calcMid(anySel.price, anySel.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, bot.baseScale, Side.sell, midPrice, bot.strategy)
        val sl = calcMid._3 - 1
        set(calcMid._1, calcMid._2, calcMid._2, bot.buyGridLevels, sl)

      case (a, s) if a != 0 && s != 0 =>
        val anySel = sortedSels.head
        val calcMidSel = Strategy.calcMid(anySel.price, anySel.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, bot.baseScale, Side.sell, midPrice, bot.strategy)
        val anyBuy = sortedBuys.head
        val calcMidBuy = Strategy.calcMid(anyBuy.price, anyBuy.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, bot.baseScale, Side.buy, calcMidSel._1, bot.strategy)
        val bl = calcMidBuy._3 - 1
        val sl = calcMidSel._3 - 1
        set(calcMidSel._1, calcMidBuy._2, calcMidSel._2, bl, sl)
    }

    res ++= Strategy.seed(buyQty, calcMidPrice, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, levels = buyLevels, gridSpace = bot.gridSpace, side = Side.buy, act = PingPong.ping, isPulledFromOtherSide = false, strategy = bot.strategy, isNoQtyCutoff = bot.isNoQtyCutoff, maxPrice = bot.maxPrice, minPrice = bot.minPrice)
    res ++= Strategy.seed(selQty, calcMidPrice, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, selLevels, bot.gridSpace, Side.sell, PingPong.ping, isPulledFromOtherSide = false, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)
    res
  }

  def counter(order: Offer): Offer = Strategy.counter(order.quantity, order.price, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, bot.gridSpace, order.side, PingPong.pong, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)

  def grow(sortedBuys: Seq[Offer], sortedSels: Seq[Offer], side: Side): Seq[Offer] = {
    def matcher(side: Side): Seq[Offer] = {
      val preSeed = getRuntimeSeedStart(sortedBuys, sortedSels, side)
      Strategy.seed(preSeed._2, preSeed._3, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, preSeed._1, bot.gridSpace, side, PingPong.ping, preSeed._4, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)
    }

    side match {
      case Side.buy => matcher(Side.buy)
      case Side.sell => matcher(Side.sell)
      case _ => Seq.empty[Offer] // unsupported operation at runtime
    }
  }

  def trim(sortedBuys: Seq[Offer], sortedSels: Seq[Offer], side : Side): Seq[Offer] = {
    val (orders, limit) = if (side == Side.buy) (sortedBuys, bot.buyGridLevels) else (sortedSels, bot.sellGridLevels)
    orders.slice(limit, orders.size)
  }

  def getRuntimeSeedStart(sortedBuys: Seq[Offer], sortedSels: Seq[Offer], side: Side): (Int, BigDecimal, BigDecimal, Boolean) = {
    var isPulledFromOtherSide: Boolean = false
    var levels: Int = 0

    val q0p0 = side match {
      case Side.buy =>
        sortedBuys.size match {
          case 0 =>
            val topSel = sortedSels.head
            levels = bot.buyGridLevels
            isPulledFromOtherSide = true
            (topSel.quantity, topSel.price)
          case _ =>
            val lowBuy = sortedBuys.last
            levels = bot.buyGridLevels - sortedBuys.size
            (lowBuy.quantity, lowBuy.price)
        }
      case Side.sell =>
        sortedSels.size match {
          case 0 =>
            val topBuy = sortedBuys.head
            levels = bot.sellGridLevels
            isPulledFromOtherSide = true
            (topBuy.quantity, topBuy.price)
          case _ =>
            val lowSel = sortedSels.last
            levels = bot.sellGridLevels - sortedSels.size
            (lowSel.quantity, lowSel.price)
        }
      case _ => (BigDecimal("0"), BigDecimal("0"))
    }
    (levels, q0p0._1, q0p0._2, isPulledFromOtherSide)
  }

}
