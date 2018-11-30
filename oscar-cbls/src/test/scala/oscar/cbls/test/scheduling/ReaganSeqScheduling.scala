package oscar.cbls.test.scheduling

import oscar.cbls.algo.boundedArray.BoundedArray
import oscar.cbls.business.seqScheduling.model._
import oscar.cbls.business.seqScheduling.neighborhood.{ReinsertActivity, SwapActivity}
import oscar.cbls.core.computation.Store

object ReaganSeqScheduling {
  val model = Store(checker = None, noCycle=false)
  // Activities
  val eat = new Activity(model, "Eat", 2)
  val sleep = new Activity(model, "Sleep", 8)
  val think = new Activity(model, "Think", 12)
  val chew = new Activity(model, "Chew", 3)
  val speak = new Activity(model, "Speak", 3)
  val drink = new Activity(model, "Drink", 2)
  val activities = new BoundedArray[Activity](6, Activity.setIndex)
  activities.:::(List(eat, sleep, think, chew, speak, drink))
  // Reagan Resource
  // Default Running Mode
  val defaultRM = new RunningMode("Default", 0)
  val reaganRMs = new RunningModeResources(1)
  reaganRMs.addRunningMode(defaultRM)
  val reagan = new Resource(model, "Reagan", 3, reaganRMs)
  val resources = new BoundedArray[Resource](1, Resource.setIndex)
  resources :+ reagan
  // Precedences
  val precedences = new Precedences(6)
  precedences.addPrecedence(think, drink)
  precedences.addPrecedence(eat, sleep)
  precedences.addPrecedence(chew, speak)
  // Resource usages
  val resUsages = new ActivityResourceUsages(6, 1)
  resUsages.addActivityResourceUsage(eat, reagan, defaultRM, 2)
  resUsages.addActivityResourceUsage(sleep, reagan, defaultRM, 3)
  resUsages.addActivityResourceUsage(chew, reagan, defaultRM, 1)
  resUsages.addActivityResourceUsage(think, reagan, defaultRM, 1)
  resUsages.addActivityResourceUsage(speak, reagan, defaultRM, 3)
  resUsages.addActivityResourceUsage(drink, reagan, defaultRM, 3)
  // Scheduling Problem
  val scProblem = new SchedulingProblem(model, activities, resources, precedences, resUsages)
  // Model closed
  model.close()

  println("Model closed")

  def main(args: Array[String]): Unit = {
    // Neighborhoods
    val swapNH = new SwapActivity(scProblem, "Swap")
    val reinsertNH = new ReinsertActivity(scProblem, "Reinsert")
    val combinedNH = reinsertNH best swapNH
    // This is the search strategy
    combinedNH.doAllMoves(obj = scProblem.mkspObj)
    // And here, the results
    println(s"*************** RESULTS ***********************************")
    println(s"Schedule makespan = ${scProblem.makeSpan.value}")
    println(s"Scheduling sequence = ${scProblem.activitiesPriorList.value}")
    println("Scheduling start times = [  ")
    scProblem.startTimes.foreach(v => println(s"    $v"))
    println("]")
    println(s"Scheduling setup times: ${scProblem.setupTimes}")
  }
}
