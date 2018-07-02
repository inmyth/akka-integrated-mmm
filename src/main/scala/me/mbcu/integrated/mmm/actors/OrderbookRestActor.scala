package me.mbcu.integrated.mmm.actors

import akka.actor.{Actor, ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderbookRestActor._
import me.mbcu.integrated.mmm.ops.Definitions.Settings
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.sequences.Strategy
import me.mbcu.integrated.mmm.sequences.Strategy.PingPong
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OrderbookRestActor {

  case class PlaceOrders(order : Seq[Offer], as : String)

  case class CheckOrders(ids : Seq[String])

  case class CancelOrders(orders : Seq[Offer], as: String)

  object GetLastTrade

}
class OrderbookRestActor(bot:Bot) extends Actor with MyLogging {
  private implicit val ec: ExecutionContextExecutor = global
  var sels: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var buys: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var sortedSels: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var sortedBuys: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var refreshCancellable: Option[Cancellable] = None
  var logCancellable: Option[Cancellable] = None
  private var op: Option[ActorRef] = None

  def receive: Receive = {

    case "start" =>
      op = Some(sender())
      logCancellable = Some(context.system.scheduler.schedule(15 second, Settings.orderbookLogSeconds.id seconds, self, "log"))
      sender ! GetOrderbook(1)

    case "log" => info(Offer.dump(sortedBuys, sortedSels))

    case GotOrderbook(offers, currentPage, nextPage) =>
      offers.foreach(self ! GotOrderInfo(_))
      if (nextPage) {
        op.foreach(_ ! GetOrderbook(currentPage + 1))
      } else {
        self ! "keep or clear orderbook"
      }

    case "keep or clear orderbook" =>
      bot.seed match {
        case a if a.equalsIgnoreCase(StartingPrice.lastOwn.toString) | a.equalsIgnoreCase(StartingPrice.lastTicker.toString) =>
          if (sels.isEmpty && buys.isEmpty) self ! "init price" else op.foreach(_ ! cancelOrders((buys ++ sels).toSeq.map(_._2), "Clear orderbook" ))
        case _ => self ! "init price"
      }

    case "init price" => op.foreach(_ ! GetLastTrade)

    case "refresh orders" =>
      (sels.size, buys.size) match {
        case (0,0) => self ! "init price"
        case _ =>
          self ! "balancer"
          if (bot.isStrictLevels) self ! "trim"
          op.foreach(_ ! CheckOrders((buys ++ sels).toSeq.map(_._1)))
      }

    case "balancer" =>
      val growth = grow(Side.buy) ++ grow(Side.sell)
      sendOrders(growth, "balancer")

    case "trim" =>
      val trims = trim(Side.buy) ++ trim(Side.sell)
      cancelOrders(trims, "Level cap")

    case GotOrderCancelled(id) =>
      resetRefresh()
      remove(Side.sell, id)
      remove(Side.buy, id)
      sortBoth()

    case GotStartPrice(price) =>
      price match {
        case Some(p) =>
          val seed = initialSeed(p)
          sendOrders(seed, "seed")
        case _ =>
      }

    case GotOrderInfo(offer) =>
      resetRefresh()
      offer.status match {

        case Some(Status.unfilled) =>
          add(offer)
          sort(offer.side)

        case Some(Status.filled) =>
          remove(offer.side, offer.id)
          sort(offer.side)
          val counters = counter(offer)
          sendOrders(counters, "counter")

        case Some(Status.partialFilled) =>
          add(offer)
          sort(offer.side)

        case Some(Status.cancelled | Status.cancelInProcess) =>
          remove(offer.side, offer.id)
          sort(offer.side)

        case _ => info(s"OrderbookActor_${bot.pair}#GotOrderInfo : unrecognized offer status")

      }

    case "log orderbooks" => info(Offer.dump(sortedBuys, sortedSels))
  }

  def resetRefresh(): Unit ={
    refreshCancellable.foreach(_.cancel())
    refreshCancellable = Some(context.system.scheduler.scheduleOnce(Settings.int5.id second, self, "refresh orders"))
  }

  def sendOrders(offers: Seq[Offer], as: String): Unit = op foreach(_ ! PlaceOrders(offers, as))

  def cancelOrders(offers: Seq[Offer], as: String): Unit = op foreach(_ ! CancelOrders(offers, as))

  def sortBoth(): Unit = {
    sort(Side.buy)
    sort(Side.sell)
  }

  def initialSeed(midPrice: BigDecimal): Seq[Offer] = {
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
        val calcMid = Strategy.calcMid(anyBuy.price, anyBuy.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, Side.buy, midPrice, bot.strategy)
        val bl = calcMid._3 - 1
        set(calcMid._1, calcMid._2, calcMid._2, bl, bot.sellGridLevels)

      case (a, s) if a == 0 && s != 0 =>
        val anySel = sortedSels.head
        val calcMid = Strategy.calcMid(anySel.price, anySel.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, Side.sell, midPrice, bot.strategy)
        val sl = calcMid._3 - 1
        set(calcMid._1, calcMid._2, calcMid._2, bot.buyGridLevels, sl)

      case (a, s) if a != 0 && s != 0 =>
        val anySel = sortedSels.head
        val calcMidSel = Strategy.calcMid(anySel.price, anySel.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, Side.sell, midPrice, bot.strategy)
        val anyBuy = sortedBuys.head
        val calcMidBuy = Strategy.calcMid(anyBuy.price, anyBuy.quantity, bot.quantityPower, bot.gridSpace, bot.counterScale, Side.buy, calcMidSel._1, bot.strategy)
        val bl = calcMidBuy._3 - 1
        val sl = calcMidSel._3 - 1
        set(calcMidSel._1, calcMidBuy._2, calcMidSel._2, bl, sl)
    }

    res ++= Strategy.seed(buyQty, calcMidPrice, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, levels = buyLevels, gridSpace = bot.gridSpace, side = Side.buy, act = PingPong.ping, isPulledFromOtherSide = false, strategy = bot.strategy, isNoQtyCutoff = bot.isNoQtyCutoff, maxPrice = bot.maxPrice, minPrice = bot.minPrice)
    res ++= Strategy.seed(selQty, calcMidPrice, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, selLevels, bot.gridSpace, Side.sell, PingPong.ping, isPulledFromOtherSide = false, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)
    res
  }

  def counter(order: Offer): Seq[Offer] = Strategy.counter(order.quantity, order.price, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, bot.gridSpace, order.side, PingPong.pong, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)

  def grow(side: Side): Seq[Offer] = {
    def matcher(side: Side): Seq[Offer] = {
      val preSeed = getRuntimeSeedStart(side)
      Strategy.seed(preSeed._2, preSeed._3, bot.quantityPower, bot.counterScale, bot.baseScale, bot.pair, preSeed._1, bot.gridSpace, side, PingPong.ping, preSeed._4, bot.strategy, bot.isNoQtyCutoff, bot.maxPrice, bot.minPrice)
    }

    side match {
      case Side.buy => matcher(Side.buy)
      case Side.sell => matcher(Side.sell)
      case _ => Seq.empty[Offer] // unsupported operation at runtime
    }
  }

  def trim(side : Side) : Seq[Offer] = {
    val (orders, limit) = if (side == Side.buy) (sortedBuys, bot.buyGridLevels) else (sortedSels, bot.sellGridLevels)
    // this may cause a hole.
    orders.slice(limit, orders.size)
  }

  def getRuntimeSeedStart(side: Side): (Int, BigDecimal, BigDecimal, Boolean) = {
    var isPulledFromOtherSide: Boolean = false
    var levels: Int = 0

    val q0p0 = side match {
      case Side.buy =>
        sortedBuys.size match {
          case 0 =>
            levels = bot.buyGridLevels
            isPulledFromOtherSide = true
            (getTopSel.quantity, getTopSel.price)
          case _ =>
            levels = bot.buyGridLevels - sortedBuys.size
            (getLowBuy.quantity, getLowBuy.price)
        }
      case Side.sell =>
        sortedSels.size match {
          case 0 =>
            levels = bot.sellGridLevels
            isPulledFromOtherSide = true
            (getTopBuy.quantity, getTopBuy.price)
          case _ =>
            levels = bot.sellGridLevels - sortedSels.size
            (getLowSel.quantity, getLowSel.price)
        }
      case _ => (BigDecimal("0"), BigDecimal("0"))
    }
    (levels, q0p0._1, q0p0._2, isPulledFromOtherSide)
  }

  def add(offer: Offer): Unit = {
    var l = offer.side match {
      case Side.buy => buys
      case Side.sell => sels
      case _ => TrieMap.empty[String, Offer]
    }
    l += (offer.id -> offer)
  }

  def remove(side: Side, clientOrderId: String): Unit = {
    var l = side match {
      case Side.buy => buys
      case Side.sell => sels
      case _ => TrieMap.empty[String, Offer]
    }
    l -= clientOrderId
  }

  def sort(side: Side): Unit = {
    side match {
      case Side.buy => sortedBuys = sortBuys(buys)
      case Side.sell => sortedSels = sortSels(sels)
      case _ =>
    }
  }

  def sortBuys(buys: TrieMap[String, Offer]): scala.collection.immutable.Seq[Offer] =
    collection.immutable.Seq(buys.toSeq.map(_._2).sortWith(_.price > _.price): _*)

  def sortSels(sels: TrieMap[String, Offer]): scala.collection.immutable.Seq[Offer] =
    collection.immutable.Seq(sels.toSeq.map(_._2).sortWith(_.price < _.price): _*)

  def getTopSel: Offer = sortedSels.head

  def getLowSel: Offer = sortedSels.last

  def getTopBuy: Offer = sortedBuys.head

  def getLowBuy: Offer = sortedBuys.last
}
