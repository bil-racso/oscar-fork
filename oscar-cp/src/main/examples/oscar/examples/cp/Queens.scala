package oscar.examples.cp

import oscar.cp._
import oscar.algo.search.NewSearch
import oscar.algo.reversible.ReversibleBoolean
import oscar.algo.search.NewSearch
import oscar.cp.searches.BinaryLastConflict

/**
 * n-queens model: place n-queens on a chess-board such that they don't attack each other.
 * this program search for all the solutions
 * Using Non Deterministic Search
 *
 * @author Pierre Schaus pschaus@gmail.com
 * @author Renaud Hartert ren.hartert@gmail.com
 */
object Queens extends CPModel with App {

  val nQueens = 10 // Number of queens
  val Queens = 0 until nQueens

  // Variables
  val queens = Array.fill(nQueens)(CPIntVar.sparse(0, nQueens - 1))

  // Constraints
  add(allDifferent(queens))
  add(allDifferent(Queens.map(i => queens(i) + i)))
  add(allDifferent(Queens.map(i => queens(i) - i)))
  
  // Search heuristic
  //search(binaryFirstFail(queens))
  
  search(new BinaryLastConflict(queens, i => queens(i).size, i => queens(i).min))
  
  // Execution
  val stats = start()
  println(stats)
}
