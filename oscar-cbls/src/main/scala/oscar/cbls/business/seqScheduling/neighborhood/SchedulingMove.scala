package oscar.cbls.business.seqScheduling.neighborhood

import oscar.cbls.business.seqScheduling.model.SchedulingProblem
import oscar.cbls.core.computation.Variable
import oscar.cbls.core.search.{Move, Neighborhood}

abstract class SchedulingMove(schP: SchedulingProblem,
                              val neighborhood:Neighborhood,
                              override val neighborhoodName:String = "",
                              override val objAfter: Int)
  extends Move(objAfter, neighborhoodName) {

  override def touchedVariables: List[Variable] = List(schP.activitiesPriorList)

  def impactedActivities: Iterable[Int]
}