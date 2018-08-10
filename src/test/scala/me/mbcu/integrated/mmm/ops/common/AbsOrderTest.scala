package me.mbcu.integrated.mmm.ops.common

import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar

class AbsOrderTest extends FunSuite with MockitoSugar{

  test ("get duplicates from list with duplicates") {

    val symbol = "noah_rur"
    val active = Status.active
    val side = Side.sell // symbol, side, BigDecimal("0.07742766"),
    val offers = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098403259", symbol, side, active, 3L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403224", symbol, side, active, 9L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098402116", symbol, side, active, 6L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098403227", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098402119", symbol, side, active, 8L, None, BigDecimal(2014), BigDecimal(".07582400"), None)
    )
    scala.util.Random.shuffle(offers)
    val res = AbsOrder.getDuplicates(offers)

    assert(res.size === 3)
    assert(res.head.price === BigDecimal(".07582417"))
    assert(res(1).price === BigDecimal(".07582417"))
    assert(res.last.price === BigDecimal(".07635494"))

    val noDup = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None),
      Offer("2073861098403227", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098402119", symbol, side, active, 8L, None, BigDecimal(2014), BigDecimal(".07582400"), None)
    )
    scala.util.Random.shuffle(noDup)
    val res2 = AbsOrder.getDuplicates(noDup)
    assert(res2.size === 0)

    val one = Seq(
    //  id:1073861122230849 quantity:2000 price:0.06267738 filled:0
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(2000), BigDecimal(".06267738"), None)

    )
    val res3 = AbsOrder.getDuplicates(one)
    assert(res3.size === 0)

    val singles = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None)
    )
    val res4 = AbsOrder.getDuplicates(singles)
    assert(res4.size === 0)
  }

  test("merge trims and cancel dupes") {
    val symbol = "noah_rur"
    val active = Status.active
    val side = Side.sell // symbol, side, BigDecimal("0.07742766"),

    val trims1 = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None)
    )

    val dupes1 = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098403227", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098403228", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098403229", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None),
      Offer("2073861098403230", symbol, side, active, 7L, None, BigDecimal(2028), BigDecimal(".07582400"), None)
    )

    val res1 = AbsOrder.margeTrimAndDupes(trims1, dupes1)
    assert(res1._1.size === 2)
    assert(res1._2.size === 6)


    val trims2 = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None)
    )

    val dupes2 = Seq()

    val res2 = AbsOrder.margeTrimAndDupes(trims2, dupes2)
    assert(res2._1.size === 4)
    assert(res2._2.size === 0)

    val trims3 = Seq()

    val dupes3 = Seq(
      Offer("2073861098400280", symbol, side, active, 1L, None, BigDecimal(1972), BigDecimal(".07742766"), None),
      Offer("2073861098400222", symbol, side, active, 2L, None, BigDecimal(1986), BigDecimal(".07688943"), None),
      Offer("2073861098402160", symbol, side, active, 4L, None, BigDecimal(2000), BigDecimal(".07635494"), None),
      Offer("2073861098403220", symbol, side, active, 5L, None, BigDecimal(2014), BigDecimal(".07582417"), None)
    )

    val res3 = AbsOrder.margeTrimAndDupes(trims3, dupes3)
    assert(res3._1.size === 0)
    assert(res3._2.size === 4)
  }

}
