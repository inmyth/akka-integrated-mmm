package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OrderGIActor.{CheckSafeForGI, SafeForGI}
import me.mbcu.integrated.mmm.ops.Definitions.Settings
import me.mbcu.integrated.mmm.ops.common.AbsOrder.{CheckSafeForSeed, QueueRequest, SafeForSeed}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object OrderGIActor {

  case class CheckSafeForGI(ref: ActorRef, bot: Bot)

  case class SafeForGI(yes: Boolean)

}

class OrderGIActor(bot: Bot, exchange: AbsExchange) extends AbsOrder(bot) with MyLogging{
  private implicit val ec: ExecutionContextExecutor = global
  var sels: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var buys: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var sortedSels: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var sortedBuys: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var maintainCancellable: Option[Cancellable] = None
  var logCancellable: Option[Cancellable] = None
  var seedCancellable: Option[Cancellable] = None
  private var op: Option[ActorRef] = None
  private implicit val book: ActorRef = self
  private implicit val imBot: Bot = bot

  override def receive: Receive = {

    case "start" =>
      op = Some(sender())
      logCancellable = Some(context.system.scheduler.schedule(15 second, Settings.intervalLogSeconds seconds, self, message="log"))
      queue1(GetActiveOrders(-1, Seq.empty[Offer], 1, As.Init))

    case "log" => info(Offer.dump(bot, sortedBuys, sortedSels))

    case GotActiveOrders(offers, currentPage, nextPage, arriveMs, send) =>
      if (nextPage) {
        queue1(GetActiveOrders(-1, offers, currentPage + 1, As.Init))
      } else {
        offers.foreach(add)
        sortBoth()
        self ! "keep or clear orderbook"
      }

    case "keep or clear orderbook" =>
      bot.seed match {
        case a if a.equalsIgnoreCase(StartMethods.lastOwn.toString) | a.equalsIgnoreCase(StartMethods.lastTicker.toString) =>
          if (sels.isEmpty && buys.isEmpty) self ! "init price" else cancelOrders((buys ++ sels).toSeq.map(_._2), As.ClearOpenOrders)
        case _ =>
          self ! "init price"
          info(s"Book ${bot.exchange} ${bot.pair} : ${bot.seed} ")
      }

    case "init price" =>
      bot.seed match {
        case s if s contains "last" => s match {
          case m if m.equalsIgnoreCase(StartMethods.lastOwn.toString) => queue1(GetOwnPastTrades(As.Init))
          case m if m.equalsIgnoreCase(StartMethods.lastTicker.toString) => queue1(GetTickerStartPrice(As.Init))
        }
        case s if s contains "cont" => gotStartPrice(None)
        case _ => gotStartPrice(Some(BigDecimal(bot.seed)))
      }

    case "maintain" => op.foreach(_ ! CheckSafeForGI(self, bot))

    case SafeForGI(yes) =>
      if (!yes) {
        scheduleMaintain(1)
      } else {
        queue((sortedBuys ++ sortedSels).map(_.id).map(GetOrderInfo(_, As.RoutineCheck)))
      }

    case "reseed" => op.foreach(_ ! CheckSafeForSeed(self, bot))

    case SafeForSeed(yes) =>
      if (yes) {
        val growth = grow(sortedBuys, sortedSels, Side.buy) ++ grow(sortedBuys, sortedSels, Side.sell)
        sendOrders(growth, As.Seed)
      }

    case GotOrderCancelled(as, arriveMs, send) =>
      remove(send.offer.side, send.offer.id)
      sort(send.offer.side)
      as match {
        case As.ClearOpenOrders => if (buys.isEmpty && sels.isEmpty) self ! "init price"
        case _ =>
      }

    case GotStartPrice(price, arriveMs, send) => gotStartPrice(price)

    case GotProvisionalOffer(newId, provisionalTs, noIdoffer) =>
      val offer = noIdoffer.copy(id = newId, createdAt = provisionalTs) // provisionalTs(e.g server time) is needed to correctly remove duplicates
      offer.side match {
        case Side.buy => buys += (offer.id -> offer)
        case _ => sels += (offer.id -> offer)
      }
      sort(offer.side)
      queue1(GetOrderInfo(offer.id , As.RoutineCheck))

    case GotOrderInfo(offer, arriveMs, send) =>
      scheduleMaintain(1)
      offer.status match {

        case Status.active =>
          addSort(offer)
          val dupes = AbsOrder.getDuplicates(sortedBuys) ++ AbsOrder.getDuplicates(sortedSels)
          val trims = if (bot.isStrictLevels) trim(sortedBuys, sortedSels, Side.buy) ++ trim(sortedBuys, sortedSels, Side.sell) else Seq.empty[Offer]
          val cancels = AbsOrder.margeTrimAndDupes(trims, dupes)
          cancelOrders(cancels._1, As.Trim)
          cancelOrders(cancels._2, As.KillDupes)

        case Status.filled =>
          removeSort(offer)
          val ctr = counter(offer)
          sendOrders(Seq(ctr), as = As.Counter)

        case Status.partiallyFilled => addSort(offer)

        case Status.cancelled => removeSort(offer)

        case _ => info(s"OrderbookActor_${bot.pair}#GotOrderInfo : unrecognized offer status")

      }
  }

  def gotStartPrice(price: Option[BigDecimal]) : Unit = {
    scheduleMaintain(Settings.getActiveSeconds)
    scheduleSeed(Settings.intervalSeedSeconds)
    price match {
      case Some(p) =>
        info(s"Got initial price ${bot.exchange} ${bot.pair} : $price, starting operation")
        val seed = initialSeed(sortedBuys, sortedSels, p)
        sendOrders(seed, As.Seed)
      case _ => error(s"Orderbook#GotStartPrice : Starting price for ${bot.exchange} / ${bot.pair} not found. Try different startPrice in bot")
    }
  }

  def queue(reqs: Seq[SendRest]): Unit = op foreach(_ ! QueueRequest(reqs))

  def queue1(req: SendRest): Unit = queue(Seq(req))

  def sendOrders(offers: Seq[Offer], as: As): Unit = queue(offers.map(NewOrder(_, as)))

  def cancelOrders(offers: Seq[Offer], as: As): Unit = queue(offers.map(CancelOrder(_, as)))

  def reseed(side: Side): Seq[Offer] = side match {
      case Side.buy => grow(sortedBuys, sortedSels, Side.buy)
      case _ => grow(sortedBuys, sortedSels, Side.sell)
  }

  def scheduleSeed(s: Int): Unit = {
    seedCancellable.foreach(_.cancel)
    seedCancellable = Some(context.system.scheduler.schedule(initialDelay = 5 second, s second, self, "reseed"))
  }

  def scheduleMaintain(s: Int): Unit = {
    maintainCancellable.foreach(_.cancel)
    maintainCancellable = Some(context.system.scheduler.scheduleOnce(s second, self, "maintain"))
  }

  def addSort(offer: Offer):Unit = {
    add(offer)
    sort(offer.side)
  }

  def sort(side: Side): Unit = {
    side match {
      case Side.buy => sortedBuys = Offer.sortBuys(buys.values.toSeq)
      case _ => sortedSels = Offer.sortSels(sels.values.toSeq)
    }
  }

  def removeSort(offer: Offer): Unit = {
    remove(offer.side, offer.id)
    sort(offer.side)
  }

  def remove(side: Side, clientOrderId: String): Unit = {
    var l = side match {
      case Side.buy => buys
      case Side.sell => sels
      case _ => TrieMap.empty[String, Offer]
    }
    l -= clientOrderId
  }

  def sortBoth(): Unit = {
    sort(Side.buy)
    sort(Side.sell)
  }

  def add(offer: Offer): Unit = {
    var l = offer.side match {
      case Side.buy => buys
      case Side.sell => sels
      case _ => TrieMap.empty[String, Offer]
    }
    l += (offer.id -> offer)
  }

}
