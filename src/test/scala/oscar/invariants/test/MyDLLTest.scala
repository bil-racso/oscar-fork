/**
 * *****************************************************************************
 * This file is part of OscaR (Scala in OR).
 *
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/gpl-3.0.html
 * ****************************************************************************
 */

package oscar.invariants.test

import org.scalatest.FunSuite
import scala.collection.immutable._
import oscar.invariants._
import org.scalacheck.Prop._
import org.scalacheck._
import org.scalatest.prop.Checkers
import org.scalacheck.Test._
import java.util.Arrays
import oscar.invariants.MyDLL


class MyDLLTest extends FunSuite with Checkers {

	def listGen = Gen.listOf[Int](Gen.choose(-10000000, 10000000))

  test("Some Operations on Lists Of Int") {
    
    forAll(listGen) { list: List[Int] =>
      // Create a double linked list with elements of the list
      val dll = new MyDLL[Int]
      for (l <- list) dll.add(l)

      // Do some operations on both lists
      list.foreach(l => l*l  - l + 5)
      dll.foreach(l => l*l - l + 5)

      // Elements should be the same
      equivalent(list, dll)
    }
  }

  // Assert a list and a MyDLL are equals ( contain the same elements )
  def equivalent(l1: List[Int], l2: MyDLL[Int]) : Boolean = {
    if (l1.size != l2.size) false

    var equal = true
    var current = l2.first
    for (l <- l1) {
      equal = equal && (l == current.apply);
      current = current.next
    }
    equal
  }
}