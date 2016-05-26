/*******************************************************************************
  * OscaR is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Lesser General Public License as published by
  * the Free Software Foundation, either version 2.1 of the License, or
  * (at your option) any later version.
  *
  * OscaR is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Lesser General Public License  for more details.
  *
  * You should have received a copy of the GNU Lesser General Public License along with OscaR.
  * If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
  ******************************************************************************/

package oscar.cp.lineardfs

import oscar.algo.search._
import oscar.algo.search.listener.DFSearchListener

import scala.collection.mutable.ArrayBuffer

/**
  * @author Sascha Van Cauwelart
  */
class DFSLinearizer extends DFSearchListener {

  private[this] val searchStateModificationList_ : ArrayBuffer[Decision] = ArrayBuffer[Decision]()

  def searchStateModifications: Array[Decision] = searchStateModificationList_ toArray

  // called on Push events
  def onPush(node: DFSearchNode): Unit = {
    searchStateModificationList_ += new Push(node)
  }

  // called on Pop events
  def onPop(node: DFSearchNode): Unit = {
    searchStateModificationList_ += new Pop(node)
  }

  // called on branching
  def onBranch(alternative: Alternative): Unit = {
    searchStateModificationList_ += new AlternativeDecision(alternative)
  }
}
