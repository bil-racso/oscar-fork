/**
 * *****************************************************************************
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
 * ****************************************************************************
 */
package oscar.cp.constraints

import oscar.cp.core.variables.CPIntVar
import oscar.algo.reversible.ReversibleInt
import oscar.cp.core.Constraint
import oscar.cp.core.CPStore
import oscar.cp.core.CPPropagStrength
import oscar.cp.core.CPOutcome
import oscar.cp.core.CPOutcome._

/**
 * Implementation of AllDifferent
 * @author Pierre Schaus pschaus@gmail.com
 * @author Renaud Hartert ren.hartert@gmail.com
 */
class AllDiffFWC(x: Array[CPIntVar]) extends Constraint(x(0).store, "AllDiffFWC") {

  // High priority
  priorityL2 = CPStore.MaxPriorityL2 - 1

  // Active variables
  private[this] val variables = x.clone()
  private[this] val nVariables = variables.length
  private[this] val nBoundsRev = new ReversibleInt(s, 0)
  private[this] var nBounds = 0

  final override def setup(l: CPPropagStrength): CPOutcome = {
    if (propagate() == Failure) Failure
    else {
      var i = variables.length
      while (i > 0) {
        i -= 1
        variables(i).callPropagateWhenBind(this)
      }
      Suspend
    }
  }

  @inline private def setBound(id: Int): Unit = {
    val tmp = variables(nBounds)
    variables(nBounds) = variables(id)
    variables(id) = tmp
    nBounds += 1
  }

  final override def propagate(): CPOutcome = {
    nBounds = nBoundsRev.value // cache 
    var i = nBounds
    while (i < nVariables) {
      val variable = variables(i)
      if (variable.isBound) {
        val value = variable.min
        var j = nBounds
        while (j < nVariables) {
          if (j != i && variables(j).removeValue(value) == Failure) return Failure
          j += 1
        }
        setBound(i)
      }
      i += 1
    }
    nBoundsRev.value = nBounds // trail
    Suspend
  }
}


