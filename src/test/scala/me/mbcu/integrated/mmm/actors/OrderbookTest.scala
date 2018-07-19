package me.mbcu.integrated.mmm.actors

import akka.actor.{ActorSystem, Props}
import akka.dispatch.ExecutionContexts.global
import me.mbcu.integrated.mmm.ops.common.Side.Side
import me.mbcu.integrated.mmm.ops.common.{Bot, Offer, Side}
import me.mbcu.integrated.mmm.sequences.Strategy
import me.mbcu.integrated.mmm.sequences.Strategy.PingPong
import org.mockito.Mock
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}
import play.api.libs.json.Json

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.ExecutionContextExecutor

class OrderbookTest extends FunSuite with BeforeAndAfter with MockitoSugar {
  var sels: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var buys: TrieMap[String, Offer] = TrieMap.empty[String, Offer]
  var sortedSels: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]
  var sortedBuys: scala.collection.immutable.Seq[Offer] = scala.collection.immutable.Seq.empty[Offer]

  val botConfig1 =
    """
      |           {
      |               "exchange" : "yobit",
      |               "credentials" : {
      |                   "pKey" : "C62",
      |                   "nonce" : "",
      |                   "signature": "90d"
      |               },
      |               "pair": "noah_eth",
      |               "seed" : "lastTicker",
      |               "gridSpace": "0.5",
      |               "buyGridLevels":3,
      |               "sellGridLevels": 3,
      |               "buyOrderQuantity": "3000",
      |               "sellOrderQuantity": "3000",
      |               "quantityPower" : 2,
      |               "counterScale" :0,
      |               "baseScale" : 8,
      |               "isStrictLevels" : true,
      |               "isNoQtyCutoff" : true,
      |               "strategy" : "ppt"
      |           }
    """.stripMargin
  val bot = Json.parse(botConfig1).as[Bot]



  before {
    buys.clear()
    sels.clear()
    sortedSels = scala.collection.immutable.Seq.empty[Offer]
    sortedBuys = scala.collection.immutable.Seq.empty[Offer]



  }

  test ("seed 1") {
    val s2 = Offer("2073821046242647", "noah_eth", Side.sell, None, None, None, BigDecimal("2955"), BigDecimal(".00000489"), None)
    val s1 = Offer("2073821046242616", "noah_eth", Side.sell, None, None, None, BigDecimal("2970"), BigDecimal(".00000486"), None)
    val s3 = Offer("2073821046242588", "noah_eth", Side.sell, None, None, None, BigDecimal("2985"), BigDecimal(".00000483"), None)

    val b1 = Offer("1073821046242493", "noah_eth", Side.buy, None, None, None, BigDecimal("3015"), BigDecimal(".00000477"), None)
    val b2 = Offer("1073821046242543", "noah_eth", Side.buy, None, None, None, BigDecimal("3047"), BigDecimal(".00000471"), None)
    val b3 = Offer("1073821046242521", "noah_eth", Side.buy, None, None, None, BigDecimal("3031"), BigDecimal(".00000474"), None)
    buys += (b1.id -> b1)
    buys += (b2.id -> b2)
    buys += (b3.id -> b3)
    sels += (s1.id -> s1)
    sels += (s2.id -> s2)
    sels += (s3.id -> s3)

    sels -= s3.id
    sort(Side.sell)
    sort(Side.buy)
    val res = grow(Side.buy) ++ grow(Side.sell)
    assert(res.head.quantity === BigDecimal("2940"))
    assert(res.head.price === BigDecimal(".00000492")) // this becomes s3 in the next test
    assert(res.head.side === Side.sell)
  }


  test ("seed 2") {
    val s3 = Offer("2073821046279900", "noah_eth", Side.sell, None, None, None, BigDecimal("2940"), BigDecimal(".00000492"), None)
    val s2 = Offer("2073821046242647", "noah_eth", Side.sell, None, None, None, BigDecimal("2955"), BigDecimal(".00000489"), None)
    val s1 = Offer("2073821046242616", "noah_eth", Side.sell, None, None, None, BigDecimal("2970"), BigDecimal(".00000486"), None)

    val b1 = Offer("1073821046278944", "noah_eth", Side.buy, None, None, None, BigDecimal("3000"), BigDecimal(".0000048"), None)
    val b2 = Offer("1073821046242493", "noah_eth", Side.buy, None, None, None, BigDecimal("3015"), BigDecimal(".00000477"), None)
    val b3 = Offer("1073821046242521", "noah_eth", Side.buy, None, None, None, BigDecimal("3031"), BigDecimal(".00000474"), None)
    buys += (b1.id -> b1)
    buys += (b2.id -> b2)
    buys += (b3.id -> b3)
    sels += (s1.id -> s1)
    sels += (s2.id -> s2)
    sels += (s3.id -> s3)

    sels -= s1.id
    sort(Side.sell)
    sort(Side.buy)
      val res = grow(Side.buy) ++ grow(Side.sell)
      println(res)
    // Vector(Offer(unused,noah_eth,sell,None,None,None,2925,0.00000495,None))
    // Request: amount=2940&method=Trade&nonce=1773457857&pair=noah_eth&rate=0.00000492&type=sell, As: Seed, yobit : noah_eth

  }

/*
{"success":1,"return":{"2073821046242616":{"pair":"noah_eth","type":"sell","start_amount":2970,"amount":0,"rate":0.00000486,"timestamp_created":"1531938296","status":1}}}

7 19, 2018 3:38:04 午前 java.util.logging.LogManager$RootLogger log
INFO:
Request: amount=2940&method=Trade&nonce=1773457857&pair=noah_eth&rate=0.00000492&type=sell, As: Seed, yobit : noah_eth
Response:
{"success":1,"return":{"received":0,"remains":2940,"order_id":2073821046280462,"funds":{"btc":0.00106096,"noah":6805.93712074,"eth":0.31408693,"trx":0},"funds_incl_orders":{"btc":0.00379397,"noah":24550.93712074,"eth":0.35732171,"trx":0},"server_time":1531939085}}
 */

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
