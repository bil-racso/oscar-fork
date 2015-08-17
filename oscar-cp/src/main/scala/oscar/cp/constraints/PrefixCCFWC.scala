package oscar.cp.constraints

import oscar.algo.reversible.ReversibleInt
import oscar.cp.core.delta.DeltaIntVar
import oscar.cp.core.{CPPropagStrength, CPOutcome, Constraint}
import oscar.cp.core.variables.CPIntVar
import CPOutcome._

/**
 * @author Victor Lecomte
 */
class PrefixCCFWC(X: Array[CPIntVar], minVal: Int, lowerLists: Array[Array[(Int, Int)]], upperLists: Array[Array[(Int, Int)]])
  extends Constraint(X(0).store, "PrefixCCFWC") {

  val nVariables = X.length
  var nRelevantVariables = 0
  val nValues = lowerLists.length
  val maxVal = minVal + nValues - 1

  var allLower: Array[Array[Int]] = null
  var allUpper: Array[Array[Int]] = null

  var lowerIdx: Array[Array[Int]] = null
  var lowerVal: Array[Array[Int]] = null
  var nLower: Array[Int] = null
  var nLowerInter: Array[Int] = null
  var lowerLast: Array[Int] = null
  
  var upperIdx: Array[Array[Int]] = null
  var upperVal: Array[Array[Int]] = null
  var nUpper: Array[Int] = null
  var nUpperInter: Array[Int] = null
  var upperLast: Array[Int] = null

  var lowerInterval: Array[Array[Int]] = null
  var lowerParentRev: Array[Array[ReversibleInt]] = null
  var lowerUntilCriticalRev: Array[Array[ReversibleInt]] = null
  var lowerPrevRev: Array[Array[ReversibleInt]] = null
  var lowerRightLimitRev: Array[Array[ReversibleInt]] = null

  var upperInterval: Array[Array[Int]] = null
  var upperParentRev: Array[Array[ReversibleInt]] = null
  var upperUntilCriticalRev: Array[Array[ReversibleInt]] = null
  var upperPrevRev: Array[Array[ReversibleInt]] = null
  var upperRightLimitRev: Array[Array[ReversibleInt]] = null

  var firstUnboundRev: Array[ReversibleInt] = null
  var prevUnboundRev: Array[Array[ReversibleInt]] = null
  var nextUnboundRev: Array[Array[ReversibleInt]] = null

  var changeBuffer: Array[Int] = null

  private def filterBounds() {
    nRelevantVariables = 0

    var vi = nValues
    while (vi > 0) {
      vi -= 1

      // Adding lower and upper bound 0 at 0, for convenience.
      lowerIdx(vi)(0) = 0
      lowerVal(vi)(0) = 0
      nLower(vi) = 1
      upperIdx(vi)(0) = 0
      upperVal(vi)(0) = 0
      nUpper(vi) = 1

      var i = 1
      while (i <= nVariables) {
        if (allLower(vi)(i) > lowerVal(vi)(nLower(vi) - 1)) {
          while (allLower(vi)(i) >= lowerVal(vi)(nLower(vi) - 1) + i - lowerIdx(vi)(nLower(vi) - 1)) {
            nLower(vi) -= 1
          }
          lowerIdx(vi)(nLower(vi)) = i
          lowerVal(vi)(nLower(vi)) = allLower(vi)(i)
          nLower(vi) += 1
        }
        if (allUpper(vi)(i) < upperVal(vi)(nUpper(vi) - 1) + i - upperIdx(vi)(nUpper(vi) - 1)) {
          while (allUpper(vi)(i) <= upperVal(vi)(nUpper(vi) - 1)) {
            nUpper(vi) -= 1
          }
          upperIdx(vi)(nUpper(vi)) = i
          upperVal(vi)(nUpper(vi)) = allUpper(vi)(i)
          nUpper(vi) += 1
        }
        i += 1
      }

      nLowerInter(vi) = nLower(vi) - 1
      nUpperInter(vi) = nUpper(vi) - 1
      lowerLast(vi) = lowerIdx(vi)(nLowerInter(vi))
      upperLast(vi) = upperIdx(vi)(nUpperInter(vi))
      nRelevantVariables = nRelevantVariables max lowerLast(vi) max upperLast(vi)
    }
  }

  private def fillBounds() {
    var vi = nValues
    while (vi > 0) {
      vi -= 1

      // TODO: Make it cleaner by traversing the given lower and upper bounds directly
      var (i, lowerI, upperI) = (0, 0, 0)
      while (i <= nVariables) {
        // Fill the lower bounds
        if (lowerI < nLower(vi) - 1 && lowerIdx(vi)(lowerI + 1) == i) {
          lowerI += 1
        }
        allLower(vi)(i) = lowerVal(vi)(lowerI)
        if (lowerI < nLower(vi) - 1 && lowerIdx(vi)(lowerI + 1) - i <= lowerVal(vi)(lowerI + 1) - lowerVal(vi)(lowerI)) {
          allLower(vi)(i) = lowerVal(vi)(lowerI + 1) - lowerVal(vi)(lowerI) + i
        }
        // Fill the upper bounds
        if (upperI < nUpper(vi) - 1 && upperIdx(vi)(upperI + 1) == i) {
          upperI += 1
        }
        allUpper(vi)(i) = upperVal(vi)(upperI) + i - upperIdx(vi)(upperI)
        if (upperI < nUpper(vi) - 1 && i - upperIdx(vi)(upperI) >= upperVal(vi)(upperI + 1) - upperVal(vi)(upperI)) {
          allUpper(vi)(i) = upperVal(vi)(upperI + 1)
        }
        i += 1
      }
    }
  }

  private def testAndDeduceBounds(): CPOutcome = {
    // Perform some deduction based on other values' lower and upper bounds
    var i = nVariables
    while (i > 0) {
      // Compute the sums
      var lowerSum = 0
      var upperSum = 0
      var vi = nValues
      while (vi > 0) {
        vi -= 1
        if (allLower(vi)(i) > allUpper(vi)(i)) return Failure
        lowerSum += allLower(vi)(i)
        upperSum += allUpper(vi)(i)
      }
      // Test the sums
      if (lowerSum > i || upperSum < i) return Failure
      // Deduce some bounds
      vi = nValues
      while (vi > 0) {
        vi -= 1
        // The lower bound will be at least the number of variables minus the sum of all other upper bounds
        allLower(vi)(i) = allLower(vi)(i) max (i - upperSum + allUpper(vi)(i))
        // The upper bound will be at most the number of variables minus the sum of all other lower bounds
        allUpper(vi)(i) = allUpper(vi)(i) min (i - lowerSum + allLower(vi)(i))
      }
      i -= 1
    }
    Suspend
  }

  private def initAndCheck(): CPOutcome = {

    lowerInterval = Array.ofDim[Array[Int]](nValues)
    val lowerParent = Array.ofDim[Array[Int]](nValues)
    val lowerUntilCritical = Array.ofDim[Array[Int]](nValues)
    val lowerRightLimit = Array.ofDim[Array[Int]](nValues)
    upperInterval = Array.ofDim[Array[Int]](nValues)
    val upperParent = Array.ofDim[Array[Int]](nValues)
    val upperUntilCritical = Array.ofDim[Array[Int]](nValues)
    val upperRightLimit = Array.ofDim[Array[Int]](nValues)

    // Give initial values to the structures
    var vi = nValues
    while (vi > 0) {
      vi -= 1

      lowerInterval(vi) = Array.ofDim[Int](lowerLast(vi))
      lowerParent(vi) = Array.tabulate(nLowerInter(vi))(lowerI => lowerI)
      // We have to add the number of unbound variables to this number
      lowerUntilCritical(vi) = Array.tabulate(nLowerInter(vi))(lowerI =>
        lowerVal(vi)(lowerI + 1) - lowerVal(vi)(lowerI) - lowerIdx(vi)(lowerI + 1) + lowerIdx(vi)(lowerI))
      lowerRightLimit(vi) = Array.tabulate(nLowerInter(vi))(lowerI => lowerIdx(vi)(lowerI + 1))

      var lowerI = 0
      while (lowerI < nLowerInter(vi)) {
        var i = lowerIdx(vi)(lowerI)
        while (i < lowerIdx(vi)(lowerI + 1)) {
          lowerInterval(vi)(i) = lowerI
          i += 1
        }
        lowerI += 1
      }

      upperInterval(vi) = Array.ofDim[Int](upperLast(vi))
      upperParent(vi) = Array.tabulate(nUpperInter(vi))(upperI => upperI)
      // We have to add the number of bound variables to this number
      upperUntilCritical(vi) = Array.tabulate(nUpperInter(vi))(upperI => upperVal(vi)(upperI + 1) - upperVal(vi)(upperI))
      upperRightLimit(vi) = Array.tabulate(nUpperInter(vi))(upperI => upperIdx(vi)(upperI + 1))

      var upperI = 0
      while (upperI < nUpperInter(vi)) {
        var i = upperIdx(vi)(upperI)
        while (i < upperIdx(vi)(upperI + 1)) {
          upperInterval(vi)(i) = upperI
          i += 1
        }
        upperI += 1
      }
    }

    // Adapt the size of the buffer
    var bufferSize = 0
    var i = nRelevantVariables
    while (i > 0) {
      i -= 1
      bufferSize = bufferSize max X(i).size
    }
    changeBuffer = Array.ofDim[Int](bufferSize)

    // Create the linked list of unbound variables
    val firstUnbound = Array.fill(nValues)(nRelevantVariables)
    val lastUnbound = Array.fill(nValues)(-1)
    val prevUnbound = Array.fill(nValues, nRelevantVariables)(-1)
    val nextUnbound = Array.fill(nValues, nRelevantVariables)(nRelevantVariables)

    // Initial count
    i = nRelevantVariables
    while (i > 0) {
      i -= 1
      val x = X(i)

      if (x.isBound) {
        val v = x.min
        if (minVal <= v && v <= maxVal) {
          val vi = v - minVal
          if (i < upperLast(vi)) {
            upperUntilCritical(vi)(upperInterval(vi)(i)) -= 1
          }
        }
      } else {
        var c = x.fillArray(changeBuffer)
        while (c > 0) {
          c -= 1
          val v = x.min
          if (minVal <= v && v <= maxVal) {
            val vi = v - minVal
            if (i < lowerLast(vi)) {
              lowerUntilCritical(vi)(lowerInterval(vi)(i)) += 1

              // Fill the linked list of unbound variables
              if (firstUnbound(vi) == nRelevantVariables) {
                firstUnbound(vi) = i
              } else {
                nextUnbound(vi)(lastUnbound(vi)) = i
                prevUnbound(vi)(i) = lastUnbound(vi)
              }
              lastUnbound(vi) = i
            }
          }
        }
      }

      // Register before the first check loop so that we receive information on what we changed there
      x.callOnChanges(i, delta => whenDomainChanges(delta, x))
    }

    // Initial checks
    vi = nValues
    while (vi > 0) {
      vi -= 1
      val v = vi + minVal

      // Merge as much as possible, intentionally backwards!
      var lowerI = nLowerInter(vi)
      while (lowerI > 1) {
        lowerI -= 1

        if (lowerUntilCritical(vi)(lowerI) <= 0) {
          lowerParent(vi)(lowerI) = lowerI - 1
          lowerUntilCritical(vi)(lowerI - 1) += lowerUntilCritical(vi)(lowerI)
          lowerRightLimit(vi)(lowerI - 1) = lowerRightLimit(vi)(lowerI)
        }
      }

      // If some of the leftmost constraints are already decided
      if (lowerUntilCritical(vi)(0) < 0) return Failure
      else if(lowerUntilCritical(vi)(0) == 0) {
        if (assignUntil(vi, v, lowerRightLimit(vi)(0)) == Failure) {
          return Failure
        }
        // If this is the only interval remaining
        else if (lowerRightLimit(vi)(0) == upperLast(vi)) {
          lowerParent(vi)(0) = -1
        }
        // Otherwise, merge it to the right
        else {
          val next = lowerInterval(vi)(lowerRightLimit(vi)(0))
          lowerParent(vi)(next) = 0
          lowerUntilCritical(vi)(0) = lowerUntilCritical(vi)(next)
          lowerRightLimit(vi)(0) = lowerRightLimit(vi)(next)
        }
      }

      // Redirect variables
      lowerI = nLowerInter(vi)
      while (lowerI > 0) {
        lowerI -= 1

        if (lowerParent(vi)(lowerI) == lowerI) {
          var i = lowerIdx(vi)(lowerI)
          while (i < lowerRightLimit(vi)(lowerI)) {
            lowerInterval(vi)(i) = lowerI
            i += 1
          }
        }
      }

      // Merge as much as possible, intentionally backwards!
      var upperI = nUpperInter(vi)
      while (upperI > 1) {
        upperI -= 1
        if (upperUntilCritical(vi)(upperI) <= 0) {
          upperParent(vi)(upperI) = upperI - 1
          upperUntilCritical(vi)(upperI - 1) += upperUntilCritical(vi)(upperI)
          upperRightLimit(vi)(upperI - 1) = upperRightLimit(vi)(upperI)
        }
      }

      // If some of the leftmost constraints are already decided
      if (upperUntilCritical(vi)(0) < 0) return Failure
      else if(upperUntilCritical(vi)(0) == 0) {
        if (removeUntil(vi, v, upperRightLimit(vi)(0)) == Failure) {
          return Failure
        }
        // If this is the only interval remaining
        else if (upperRightLimit(vi)(0) == upperIdx(vi)(nUpperInter(vi))) {
          upperParent(vi)(0) = -1
        }
        // Otherwise, merge it to the right
        else {
          val next = upperInterval(vi)(upperRightLimit(vi)(0))
          upperParent(vi)(next) = 0
          upperUntilCritical(vi)(0) = upperUntilCritical(vi)(next)
          upperRightLimit(vi)(0) = upperRightLimit(vi)(next)
        }
      }

      // Redirect variables
      upperI = nUpperInter(vi)
      while (upperI > 0) {
        upperI -= 1

        if (upperParent(vi)(upperI) == upperI) {
          var i = upperIdx(vi)(upperI)
          while (i < upperRightLimit(vi)(upperI)) {
            upperInterval(vi)(i) = upperI
            i += 1
          }
        }
      }
    }

    // Initialize the reversible arrays
    lowerParentRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nLowerInter(vi)))
    lowerUntilCriticalRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nLowerInter(vi)))
    lowerPrevRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nLowerInter(vi)))
    lowerRightLimitRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nLowerInter(vi)))

    upperParentRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nUpperInter(vi)))
    upperUntilCriticalRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nUpperInter(vi)))
    upperPrevRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nUpperInter(vi)))
    upperRightLimitRev = Array.tabulate(nValues)(vi => Array.ofDim[ReversibleInt](nUpperInter(vi)))
    
    // Copy the temporary values into the reversible arrays
    vi = nValues
    while (vi > 0) {
      vi -= 1

      var lowerI = 0
      var lowerPrev = -1
      while (lowerI < nLowerInter(vi)) {
        if (lowerParent(vi)(lowerI) == lowerI) {
          lowerParentRev(vi)(lowerI) = ReversibleInt(lowerI)
          lowerUntilCriticalRev(vi)(lowerI) = ReversibleInt(lowerUntilCritical(vi)(lowerI))
          lowerPrevRev(vi)(lowerI) = ReversibleInt(lowerPrev)
          lowerRightLimitRev(vi)(lowerI) = ReversibleInt(lowerRightLimit(vi)(lowerI))
          lowerPrev = lowerI
        }
        lowerI += 1
      }

      var upperI = 0
      var upperPrev = -1
      while (upperI < nUpperInter(vi)) {
        if (upperParent(vi)(upperI) == upperI) {
          upperParentRev(vi)(upperI) = ReversibleInt(upperI)
          upperUntilCriticalRev(vi)(upperI) = ReversibleInt(upperUntilCritical(vi)(upperI))
          upperPrevRev(vi)(upperI) = ReversibleInt(upperPrev)
          upperRightLimitRev(vi)(upperI) = ReversibleInt(upperRightLimit(vi)(upperI))
          upperPrev = upperI
        }
        upperI += 1
      }
    }

    Success
  }

  override def setup(l: CPPropagStrength): CPOutcome = {

    allLower = Array.fill(nValues, nVariables + 1)(0)
    allUpper = Array.fill(nValues, nVariables + 1)(nVariables)

    var vi = nValues
    while (vi > 0) {
      vi -= 1

      var lowerI = lowerLists(vi).length
      while (lowerI > 0) {
        lowerI -= 1
        if (lowerLists(vi)(lowerI)._1 < 0 || lowerLists(vi)(lowerI)._1 > nVariables) {
          throw new IllegalArgumentException("Lower bound cutoff out of range: " + lowerLists(vi)(lowerI)._1)
        } else if (lowerLists(vi)(lowerI)._2 < 0) {
          return Failure
        }
        allLower(vi)(lowerLists(vi)(lowerI)._1) = lowerLists(vi)(lowerI)._2
      }
      var upperI = upperLists(vi).length
      while (upperI > 0) {
        upperI -= 1
        if (upperLists(vi)(upperI)._1 < 0 || upperLists(vi)(upperI)._1 > nVariables) {
          throw new IllegalArgumentException("Upper bound cutoff out of range: " + upperLists(vi)(upperI)._1)
        } else if (upperLists(vi)(upperI)._2 > upperI) {
          return Failure
        }
        allUpper(vi)(upperLists(vi)(upperI)._1) = upperLists(vi)(upperI)._2
      }
    }

    lowerIdx = Array.ofDim[Int](nValues, nVariables + 1)
    lowerVal = Array.ofDim[Int](nValues, nVariables + 1)
    nLower = Array.ofDim[Int](nValues)
    nLowerInter = Array.ofDim[Int](nValues)
    lowerLast = Array.ofDim[Int](nValues)
    
    upperIdx = Array.ofDim[Int](nValues, nVariables + 1)
    upperVal = Array.ofDim[Int](nValues, nVariables + 1)
    nUpper = Array.ofDim[Int](nValues)
    nUpperInter = Array.ofDim[Int](nValues)
    upperLast = Array.ofDim[Int](nValues)

    filterBounds()
    fillBounds()
    if (testAndDeduceBounds() == Failure) return Failure
    filterBounds()

    initAndCheck()
  }

  private def whenDomainChanges(delta: DeltaIntVar, x: CPIntVar): CPOutcome = {
    val i = delta.id

    // Treat the value removals
    var c = delta.fillArray(changeBuffer)
    while (c > 0) {
      c -= 1
      val v = changeBuffer(c)
      // If the value removed is one we track
      if (minVal <= v && v <= maxVal) {
        val vi = v - minVal
        
        if (i < lowerLast(vi)) {
          removeUnbound(vi, v)

          val lowerI = findParent(lowerParentRev(vi), lowerInterval(vi)(i))
          val untilCritical = lowerUntilCriticalRev(vi)(lowerI).decr()

          if (untilCritical == 0) {
            val directPrev = lowerPrevRev(vi)(lowerI).value
            // If we are at zero
            if (directPrev == -1) {
              // Assign everything in the interval
              if (assignUntil(vi, v, lowerRightLimitRev(vi)(lowerI).value) == Failure) {
                return Failure
              }
              // Merge with next if possible
              val middleLimit = lowerRightLimitRev(vi)(lowerI).value
              if (middleLimit != lowerLast(vi)) {
                val next = findParent(lowerParentRev(vi), lowerInterval(vi)(middleLimit))

                // Compute limits to guess the preferable merge side
                val rightLimit = lowerRightLimitRev(vi)(next).value

                // Left merge
                if (2 * middleLimit > rightLimit) {
                  lowerParentRev(vi)(next).setValue(lowerI)
                  lowerUntilCriticalRev(vi)(lowerI).setValue(lowerUntilCriticalRev(vi)(next).value)
                  lowerRightLimitRev(vi)(lowerI).setValue(rightLimit)
                }
                // Right merge
                else {
                  lowerParentRev(vi)(lowerI).setValue(next)
                  lowerPrevRev(vi)(next).setValue(-1)
                }
              }
            }
            // Otherwise, we merge with the left interval
            else {
              val prev = findParent(lowerParentRev(vi), directPrev)

              // Compute limits to guess the preferable merge side
              val prevPrev = lowerPrevRev(vi)(prev).value
              val leftLimit = lowerIdx(vi)(prevPrev + 1)
              val middleLimit = lowerIdx(vi)(directPrev + 1)
              val rightLimit = lowerRightLimitRev(vi)(lowerI).value

              // Left merge
              if (2 * middleLimit >= leftLimit + rightLimit) {
                lowerParentRev(vi)(lowerI).setValue(prev)
                lowerRightLimitRev(vi)(prev).setValue(rightLimit)
              }
              // Right merge
              else {
                lowerParentRev(vi)(prev).setValue(lowerI)
                lowerUntilCriticalRev(vi)(lowerI).setValue(lowerUntilCriticalRev(vi)(prev).value)
                lowerPrevRev(vi)(lowerI).setValue(prevPrev)
              }
            }
          }
        }
      }
    }

    if (x.isBound) {
      val v = x.min
      // If the value removed is one we track
      if (minVal <= v && v <= maxVal) {
        val vi = v - minVal

        if (i < upperLast(vi)) {
          removeUnbound(vi, i)

          val upperI = findParent(upperParentRev(vi), upperInterval(vi)(i))
          val untilCritical = upperUntilCriticalRev(vi)(upperI).decr()

          if (untilCritical == 0) {
            val directPrev = upperPrevRev(vi)(upperI).value
            // If we are at zero
            if (directPrev == -1) {
              // Assign everything in the interval
              if (removeUntil(vi, v, upperRightLimitRev(vi)(upperI).value) == Failure) {
                return Failure
              }
              // Merge with next if possible
              val middleLimit = upperRightLimitRev(vi)(upperI).value
              if (middleLimit != upperLast(vi)) {
                val next = findParent(upperParentRev(vi), upperInterval(vi)(middleLimit))

                // Compute limits to guess the preferable merge side
                val rightLimit = upperRightLimitRev(vi)(next).value

                // Left merge
                if (2 * middleLimit > rightLimit) {
                  upperParentRev(vi)(next).setValue(upperI)
                  upperUntilCriticalRev(vi)(upperI).setValue(upperUntilCriticalRev(vi)(next).value)
                  upperRightLimitRev(vi)(upperI).setValue(rightLimit)
                }
                // Right merge
                else {
                  upperParentRev(vi)(upperI).setValue(next)
                  upperPrevRev(vi)(next).setValue(-1)
                }
              }
            }
            // Otherwise, we merge with the left interval
            else {
              val prev = findParent(upperParentRev(vi), directPrev)

              // Compute limits to guess the preferable merge side
              val prevPrev = upperPrevRev(vi)(prev).value
              val leftLimit = upperIdx(vi)(prevPrev + 1)
              val middleLimit = upperIdx(vi)(directPrev + 1)
              val rightLimit = upperRightLimitRev(vi)(upperI).value

              // Left merge
              if (2 * middleLimit >= leftLimit + rightLimit) {
                upperParentRev(vi)(upperI).setValue(prev)
                upperRightLimitRev(vi)(prev).setValue(rightLimit)
              }
              // Right merge
              else {
                upperParentRev(vi)(prev).setValue(upperI)
                upperUntilCriticalRev(vi)(upperI).setValue(upperUntilCriticalRev(vi)(prev).value)
                upperPrevRev(vi)(upperI).setValue(prevPrev)
              }
            }
          }
        }
      }
    }

    Suspend
  }
  
  @inline private def findParent(parentTable: Array[ReversibleInt], child: Int): Int = {
    var parent = parentTable(child).value
    if (parent == child) return child
    
    var ancestor = parentTable(parent).value
    if (ancestor == parent) return parent
    
    while (ancestor != parent) {
      parent = ancestor
      ancestor = parentTable(parent).value
    }
    parentTable(child).setValue(parent)
    parent
  }

  @inline private def removeUnbound(vi: Int, i: Int) {
    val prev = prevUnboundRev(vi)(i).value
    val next = nextUnboundRev(vi)(i).value
    
    if (prev == -1) {
      firstUnboundRev(vi).setValue(next)
    } else {
      nextUnboundRev(vi)(prev).setValue(next)
    }
    if (next != nRelevantVariables) {
      prevUnboundRev(vi)(next).setValue(prev)
    }
  }

  private def assignUntil(vi: Int, v: Int, limit: Int): CPOutcome = {
    var i = firstUnboundRev(vi).value

    // Bind all the unbound variables that have this value
    while (i < limit) {
      if (X(i).assign(v) == Failure) return Failure
      i = nextUnboundRev(vi)(i).value
    }
    prevUnboundRev(vi)(i).setValue(-1)
    firstUnboundRev(vi).setValue(i)

    Suspend
  }

  private def removeUntil(vi: Int, v: Int, limit: Int): CPOutcome = {
    var i = firstUnboundRev(vi).value

    // Bind all the unbound variables that have this value
    while (i < limit) {
      if (X(i).removeValue(v) == Failure) return Failure
      i = nextUnboundRev(vi)(i).value
    }
    prevUnboundRev(vi)(i).setValue(-1)
    firstUnboundRev(vi).setValue(i)

    Suspend
  }
}