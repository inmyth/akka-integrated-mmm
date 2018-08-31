package me.mbcu.integrated.mmm.ops.common

import akka.actor.{Actor, ActorRef}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.{As, CancelOrder, NewOrder, SendRest}
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.sequences.Strategy
import me.mbcu.integrated.mmm.sequences.Strategy.PingPong

import scala.collection.mutable

object AbsOrder {
  // find duplicates: same price, same quantity, returns the newests (for cancellation)
  def getDuplicates(offers: Seq[Offer]): Seq[Offer] =
    offers.groupBy(_.price).collect { case (x, ys) if ys.lengthCompare(1) > 0 =>
      ys.groupBy(_.quantity).collect { case (r, s) if s.lengthCompare(1) > 0 => s.sortWith(_.createdAt < _.createdAt) }
    }.flatten.flatMap(_.drop(1)).toSeq

  def isSafeForSeed(q: mutable.Queue[SendRest], nos: mutable.Set[NewOrder], bot: Bot): Boolean =
    (q.filter(_.bot == bot) ++ nos.filter(_.bot == bot).toSeq)
      .collect {
        case a: NewOrder => a
        case a: CancelOrder => a
      }
      .isEmpty


  /**
    * merge trims and killDupes while preserving As
    * @param trims
    * @param dupes
    * @return (trims, killDupes)
    */
  def margeTrimAndDupes(trims: Seq[Offer], dupes: Seq[Offer]): (Seq[Offer], Seq[Offer]) = {
    val res = (trims.map(p => (p, As.Trim)) ++ dupes.map(p => (p, As.KillDupes)))
      .map(p => p._1.id -> p).toMap
      .values
      .groupBy(p => p._2)
      .toSeq.sortWith(_._1.toString > _._1.toString)
      .map(p => (p._1, p._2.toSeq.unzip._1))
      .partition(_._1 == As.Trim)
    (res._1.unzip._2.flatten, res._2.unzip._2.flatten)
  }

  case class CheckSafeForSeed(ref: ActorRef, bot: Bot)

  case class SafeForSeed(yes: Boolean)

  case class QueueRequest(a: Seq[SendRest])


}

abstract class AbsOrder(bot: Bot) extends Actor  {

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
