package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorRef, Cancellable}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.actors.OpWsActor.QueueWs
import me.mbcu.integrated.mmm.actors.WsActor.WsRequestClient
import me.mbcu.integrated.mmm.ops.Definitions.{ErrorShutdown, Settings, ShutdownCode}
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As
import me.mbcu.integrated.mmm.ops.common.AbsRestActor.As.As
import me.mbcu.integrated.mmm.ops.common.AbsWsParser._
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common._
import me.mbcu.integrated.mmm.utils.MyLogging

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class OrderWsActor(bot: Bot, exchange: AbsExchange, req: AbsWsRequest) extends AbsOrder(bot) with MyLogging{
  private implicit val ec: ExecutionContextExecutor = global
  private val wsEx: AbsWsExchange = exchange.asInstanceOf[AbsWsExchange]
  var sels: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var buys: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var sortedSels: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var sortedBuys: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var selTrans : TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var buyTrans : TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var pendings : TrieMap[String, SendWs] = TrieMap.empty[String, SendWs]
  var isStartingPrice: Boolean = true

  var logCancellable: Option[Cancellable] = None
  private var op: Option[ActorRef] = None

  override def receive: Receive = {

    case "start" =>
      op = Some(sender())
      logCancellable = Some(context.system.scheduler.schedule(15 second, Settings.intervalLogSeconds seconds, self, message="log"))

    case "log" => info(Offer.dump(bot, sortedBuys, sortedSels))

    case WsRequestClient => // stop scheduler

    case GotSubscribe(requestId) => queue(pendings.values.toSeq)

    case GotActiveOrdersWs(offers, requestId) =>

      if (isStartingPrice) {
        addSort(offers)

        bot.seed match {
          case a if a.equals(StartMethods.lastTicker.toString) =>
            cancelOrders((buys ++ sels).values.toSeq, As.ClearOpenOrders)
            unlockInitSeed()

          case a if a.equals(StartMethods.cont.toString) => isStartingPrice = false

          case a if a.equals(StartMethods.lastOwn.toString) => op.foreach(_ ! ErrorShutdown(ShutdownCode.fatal, -1, "lastOwn is not supported"))

          case _ =>
            cancelOrders((buys ++ sels).values.toSeq, As.ClearOpenOrders)
            unlockInitSeed()

        }
      } else { // after ws gets reconnected
        val m = offers.map(p => p.id -> p).toMap
        val gones = (buys ++ sels).collect{case p @ (_: String, _: Offer) if !m.contains(p._1) => p._2}.toSeq
        prepareSend(gones.map(counter), As.Counter)

        resetAll()
        addSort(offers)

        val selGrows = grow(sortedBuys, sortedSels, Side.sell)
        val buyGrows = grow(sortedBuys, sortedSels, Side.buy)
        prepareSend(selGrows ++ buyGrows, As.Seed)
        trim()
      }

    case GotTickerPriceWs(price, requestId) =>
      queue1(req.unsubsTicker(bot.pair))
      seedFromStartPrice(price)

    case GotOfferWs(offer, requestId) =>

      offer.status match {

        case Status.filled =>
          removeSort(offer)
          prepareSend(counter(offer), As.Counter)
          prepareSend(grow(offer), As.Seed)

        case Status.partiallyFilled => addSort(offer)

        case Status.active =>
          addSort(offer)
          trim()

        case Status.cancelled =>
          removeSort(offer)
          if (isStartingPrice) {
            unlockInitSeed()
          } else {
            prepareSend(grow(offer), As.Seed)
          }

        case _ => info(s"OrderWsActor#GotOfferWs unhandled status ${offer.status} ${offer.id}")

    }

    case RemoveOfferWs(isRetry, orderId, side, requestId) =>
      if (isRetry){
        val retry = (buyTrans ++ selTrans)(orderId)
        val pending = pendings(requestId)
        prepareSend(retry, pending.as)
      }
      removeSort(side, orderId)
      removePending(requestId)

    case RemovePendingWs(requestId) => removePending(requestId)

    case RetryPendingWs(id) => queue1(pendings(id))

  }

  def unlockInitSeed(): Unit = {
    if (buys.isEmpty && sels.isEmpty) {
      isStartingPrice = false
      bot.seed match {
        case a if a.equals(StartMethods.lastTicker.toString) => queue1(req.subsTicker(bot.pair))

        case a if a.equals(StartMethods.cont.toString) =>

        case a if a.equals(StartMethods.lastOwn.toString) => // unsupported

        case _ =>
          val customStartPrice = Some(BigDecimal(bot.seed))
          seedFromStartPrice(customStartPrice)
      }
    }
  }

  def grow(offer: Offer): Seq[Offer] = grow(sortedBuys, sortedSels, offer.side)

  def removePending(requestId: String): Unit = pendings -= requestId

  def seedFromStartPrice(price: Option[BigDecimal]): Unit = price match {
      case Some(p) =>
        info(s"Got initial price ${bot.exchange} ${bot.pair} : $price, starting operation")
        val seed = initialSeed(sortedBuys, sortedSels, p)
        prepareSend(seed, As.Seed)
      case _ => error(s"Orderbook#GotStartPrice : Starting price for ${bot.exchange} / ${bot.pair} not found. Try different startPrice in bot")
    }

  def trim(): Unit = {
    val dupes = AbsOrder.getDuplicates(sortedBuys) ++ AbsOrder.getDuplicates(sortedSels)
    val trims = if (bot.isStrictLevels) trim(sortedBuys, sortedSels, Side.buy) ++ trim(sortedBuys, sortedSels, Side.sell) else Seq.empty[Offer]
    val cancels = AbsOrder.margeTrimAndDupes(trims, dupes)
    cancelOrders(cancels._1, As.Trim)
    cancelOrders(cancels._2, As.KillDupes)
  }

  def cancelOrders(o: Seq[Offer], as: As): Unit = {
    val cancels = o.map(p => req.cancelOrder(p.id, as))
    queue(cancels)
  }

  def prepareSend(o: Offer, as: As): Unit = prepareSend(Seq(o), as)

  def prepareSend(o: Seq[Offer], as: As): Unit = {
    val withId = o.map(withOrderId)
    addSort(withId)

    val newOrders = withId.map(p => req.newOrder(p, as))
    queue(newOrders)
  }

  def queue1(sendWs: SendWs): Unit = queue(Seq(sendWs))


  def queue(sendWs: Seq[SendWs]): Unit = {
    pendings ++= sendWs.map(p => p.requestId -> p)
    op foreach (_ ! QueueWs(sendWs))
  }

  def resetAll(): Unit = {
    buys.clear()
    sels.clear()
    sortedBuys = scala.collection.immutable.Seq.empty[Offer]
    sortedSels = scala.collection.immutable.Seq.empty[Offer]
  }

  def withOrderId(offer: Offer):Offer = offer.copy(id = wsEx.orderId(offer))

  def withOrderId(offers: Seq[Offer]): Seq[Offer] = offers.map(withOrderId)

  def addSort(offer: Offer): Unit = addSort(Seq(offer))

  def addSort(offers: Seq[Offer]): Unit = {
    val (b, s) = offers.partition(_.side == Side.buy)
    buys ++= b.map(p => p.id -> p)
    sels ++= s.map(p => p.id -> p)
    sort(Side.buy)
    sort(Side.sell)
  }

  def removeSort(offer: Offer): Unit = removeSort(offer.side, offer.id)

  def removeSort(side: Side, id: String): Unit = {
    remove(side, id)
    sort(side)
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
      case Side.buy => sortedBuys = Offer.sortBuys(buys.values.toSeq)
      case _ => sortedSels = Offer.sortSels(sels.values.toSeq)
    }
  }

}
