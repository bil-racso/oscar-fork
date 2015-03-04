package oscar.examples.cp

import oscar.cp._
import oscar.util._
import oscar.cp.preprocessing.ShavingUtils

/**
 * Traveling Salesman Problem with Visualization
 *
 * Given a distance matrix between 20 cities,
 * find the shortest tour visiting each city exactly once.
 *
 * @author Pierre Schaus  pschaus@gmail.com
 * @author Renaud Hartert ren.hartert@gmail.com
 */
object TSPVisu extends CPModel with App {

  // Data
  val nCities = 40
  val Cities = 0 until nCities
  val (distMatrix, coordinates) = TSPGenerator.randomInstance(nCities)

  // Variables
  val succ = Array.fill(nCities)(CPIntVar(Cities)) 
  val totDist = CPIntVar(0 to distMatrix.flatten.sum)

  // Constraints
  add(minCircuit(succ, distMatrix, totDist), Weak)
  
  println("original bound: " + totDist.min)
  
  //ShavingUtils.strengthenLowerBound(solver, succ.take(2), totDist)
  ShavingUtils.reduceDomains(solver, succ.take(2), Array(totDist))
  
  println("forward bounding: " + totDist.min)

  
  
  // Search heuristic
  /*minimize(totDist)

  search {
    // Select the not yet bound city with the smallest number of possible successors
    selectMin(Cities)(!succ(_).isBound)(succ(_).size) match {
      case None => noAlternative
      case Some(x) => {
        // Select the closest successors of the city x
        val v = selectMin(Cities)(succ(x).hasValue(_))(distMatrix(x)(_)).get
        branch(add(succ(x) == v))(add(succ(x) != v))
      }
    }
  }  
  

  // Visual Component
  val visual = new VisualTSP(coordinates, succ)

  var nSols = 0
  onSolution {
    nSols += 1
    visual.updateTour(nSols, totDist.value)
  }

  println(start())*/
}

/** Generates a random TSP instance */
object TSPGenerator {
  def randomInstance(nCities: Int, seed: Int = 0): (Array[Array[Int]], Array[(Int, Int)]) = {
    val rand = new scala.util.Random(seed)
    val coord = Array.tabulate(nCities)(i => (100 + rand.nextInt(400), rand.nextInt(400)))
    val distMatrix = Array.tabulate(nCities, nCities)((i, j) => getDist(coord(i), coord(j)))
    (distMatrix, coord)
  }

  def getDist(p1: (Int, Int), p2: (Int, Int)): Int = {
    val dx = p2._1 - p1._1
    val dy = p2._2 - p1._2
    math.sqrt(dx * dx + dy * dy).toInt
  }
}

/** Visualization for TSP */
class VisualTSP(coordinates: Array[(Int, Int)], succ: Array[CPIntVar]) {

  import oscar.visual._
  import oscar.visual.plot.PlotLine

  val Cities = 0 until coordinates.size
  val frame = VisualFrame("TSP")

  // Creates the plot and place it into the frame
  val plot = new PlotLine("", "Solution number", "Distance")
  frame.createFrame("TSP Objective Function").add(plot)

  // Creates the visualization of the tour and place it into the frame
  val tour = VisualTour(coordinates)
  frame.createFrame("TSP Tour").add(tour)
  frame.pack()

  // Updates the visualization  
  def updateTour(nSol: Int, dist: Int): Unit = {
    Cities.foreach(i => tour.edgeDest(i, succ(i).value))
    tour.repaint()
    plot.addPoint(nSol, dist)
  }
}
