package me.mbcu.integrated.mmm.utils

import org.scalatest.FunSuite

import scala.collection.mutable.ListBuffer

class MyUtilsTest extends FunSuite {


  test("test sorting shutdown code") {
    val a = new ListBuffer[(String, Option[Int])]
//    a += (("a", Some(1)))
//    a += (("b", Some(1)))
    a += (("c", Some(-1)))
    a += (("d", None))
    a += (("d", None))

    val z = a.flatMap(_._2).reduceOption(_ min _)
    assert(z === Some(-1))

  }

}
