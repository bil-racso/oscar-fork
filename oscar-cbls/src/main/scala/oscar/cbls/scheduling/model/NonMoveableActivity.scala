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

/*******************************************************************************
  * Contributors:
  *     This code has been initially developed by CETIC www.cetic.be
  *         by Renaud De Landtsheer
  ******************************************************************************/

package oscar.cbls.scheduling.model

import oscar.cbls.invariants.core.computation.{CBLSSetVar, CBLSIntVar}
import oscar.cbls.invariants.lib.minmax.MinArray
import oscar.cbls.scheduling.model.Planning
import scala.collection.immutable.SortedSet

/**
 *
 * @param startDate
 * @param duration
 * @param planning
 * @param name
 * @author renaud.delandtsheer@cetic.be
 * THIS IS EXPERIMENTAL
 * */
class NonMoveableActivity(startDate:Int, duration: CBLSIntVar, planning: Planning, name: String = "")
  extends Activity(duration: CBLSIntVar, planning: Planning, name){
  override def canAddPrecedence: Boolean = false
  override def close() {

    AdditionalPredecessors = SortedSet.empty[Int]
    AllPrecedingActivities = SortedSet.empty[Int]
    EarliestStartDate := startDate
    DefiningPredecessors = SortedSet.empty[Int]
    PotentiallyKilledPredecessors = SortedSet.empty[Int]

    AllSucceedingActivities = new CBLSSetVar(planning.model, 0, planning.activityCount - 1, "succeeding_jobs")

    //This is not correct. but since no task can be put before this one, this is not an issue.
    LatestEndDate <== MinArray(planning.LatestStartDates, AllSucceedingActivities, planning.maxduration)
  }
}

object NonMoveableActivity{
  def apply(startDate:Int, duration: CBLSIntVar, planning: Planning, name: String = "") =
    new NonMoveableActivity(startDate, duration, planning, name)
}