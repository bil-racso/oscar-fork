package oscar.cbls.business.routing.invariants.group

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


import oscar.cbls.algo.seq.{IntSequence, IntSequenceExplorer}
import oscar.cbls.core.ChangingSeqValue


sealed abstract class LogReducedSegment[T]()


/**
  * This represents a subsequence starting at startNode and ending at endNode.
  * This subsequence was present in the global sequence when the pre-computation was performed
  * @param startNode the first node of the subsequence
  * @param endNode the last node of the subsequence
  * @param steps: a list of values of type T such that
  *             if they were concatenated using the compose function,
  *             it would yield the value t between fromNode and toNode.
  *             there are O(log(n)) of thee values in the list
  * @tparam T the type of precomputation
  */
case class LogReducedPreComputedSubSequence[T](startNode:Int,
                                               endNode:Int,
                                               stepGenerator: () => List[T]) extends LogReducedSegment[T]{

  lazy val steps:List[T] = stepGenerator()

  override def toString: String = {
    "LogReducedPreComputedSubSequence(startNode:" + startNode +
      " endNode:" + endNode + " steps:{" + steps.mkString(",") + "})"
  }
}

/**
  * This represents a subsequence starting at startNode and ending at endNode.
  * This subsequence was not present in the global sequence when the pre-computation was performed, but
  * the flippedd subsequence obtained by flippig it was present in the global sequence when the pre-computation was performed, but
  * @param startNode the first node of the subsequence
  * @param endNode the last node of the subsequence
  * @param steps: a list of values of type T such that
  *             if they were concatenated using the compose function,
  *             it would yield the value t between toNode and fromNode, as in the original sequence.
  *             there are O(log(n)) of thee values in the list
  * @tparam T the type of precomputation
  */
case class LogReducedFlippedPreComputedSubSequence[T](startNode:Int,
                                                      endNode:Int,
                                                      stepGenerator: () => List[T]) extends LogReducedSegment[T]{

  lazy val steps:List[T] = stepGenerator()

  override def toString: String = {
    "LogReducedFlippedPreComputedSubSequence(startNode:" + startNode +
      " endNode:" + endNode + " steps:{" + steps.mkString(",") + "})"
  }
}

/**
  * This represent that a node that was not present in the initial sequence
  * when pre-computation was performed.
  * @param node
  */
case class LogReducedNewNode[T](node:Int, value:T) extends LogReducedSegment[T]{
  override def toString: String = {
    "LogReducedNewNode(node:" + node + ")"
  }
}




/**
  * This API provides an easy to use framework for defining a custom global constraint for vehicle routing.
  * it is to be used when the pre-computation needs to be performed on every possible sub-sequence of route,
  * thus pre-computation is O(n²)-time to achieve O(1) query time per segment.
  *
  * This particular API provides a reduction of the pre-computation time to O(n)
  * at the cost of performing the segment query in O(log(n))
  *
  * The difference in implementation is that it does not decorates evey possible segment with a value of type T.
  * Instead it decorates a subset of these segments, and each queried segment has a sequence of values of type T
  * associated with it. These values are queried from the pre-computation.
  * The assembly includes O(log(n)) of these pre-computations.
  *
  * @param routes the route of vehicles
  * @param v the number of vehicles
  * @tparam T the type of pre-computation, which is on subsequences (not on nodes)
  * @tparam U the output type of the algorithms, that you need to assign to the output variables
  */
abstract class LogReducedGlobalConstraint[T:Manifest,U:Manifest](routes:ChangingSeqValue,v :Int)
  extends GlobalConstraintDefinition[VehicleAndPosition,U](routes,v){

  /**
    * this method delivers the value of the node
    * @return the type T associated with the node "node"
    */
  def nodeValue(node: Int): T

  /**
    * this one is similar to the nodeValue except that it only is applied on vehicle,
    * to represent the return to the vehicle start at teh end of its route
    * @param vehicle
    * @return
    */
  def endNodeValue(vehicle:Int):T

  /**
    * this method is for composing steps into bigger steps.
    * @param firstStep the type T associated with stepping over a sequence of nodes (which can be minial two)
    * @param secondStep the type T associated with stepping over a sequence of nodes (which can be minial two)
    * @return the type T associated wit hthe first step followed by the second step
    */
  def composeSteps(firstStep: T, secondStep: T): T

  /**
    * this method is called by the framework when the value of a vehicle must be computed.
    *
    * @param vehicle the vehicle that we are focusing on
    * @param segments the segments that constitute the route.
    *                 The route of the vehicle is equal to the concatenation of all given segments in the order thy appear in this list
    * @return the value associated with the vehicle. This value should only be computed based on the provided segments
    */
  def computeVehicleValueComposed(vehicle: Int,
                                  segments: List[LogReducedSegment[T]]): U

  class NodeAndPreComputes(val node:Int,
                           var precomputes:Array[T] = null){
    override def toString: String = {
      "NodeAndPreComputes(node:" + node + " precomputes:" + (if(precomputes == null) null else precomputes.mkString(",")) + ")"
    }
  }

  private val vehicleToPrecomputes:Array[Array[NodeAndPreComputes]] = Array.fill(v)(null)

  def printVehicleToPrecomputes(vehicle:Int): Unit ={
    val precomputes = vehicleToPrecomputes(vehicle)
    println(precomputes.map(_.toString).mkString("\n"))
  }
  override def performPreCompute(vehicle:Int,
                                 routes:IntSequence,
                                 preComputedVals:Array[VehicleAndPosition]): Unit ={

    //println("performPreCompute(vehicle:" + vehicle + " v:" + v + " routes:" + routes)

    //identify all nodes
    identifyNodesAndAllocate(routes.explorerAtAnyOccurrence(vehicle),vehicle,0,preComputedVals)

    if(vehicleToPrecomputes(vehicle).length > 1) {
      var sequenceOfLevels = decomposeToBitNumbersMSBFirst(vehicleToPrecomputes(vehicle).length)
      //println("length of vehicle :" + vehicleToPrecomputes(vehicle).length)
      //println("sequence of levels: " + sequenceOfLevels)

      var positionInRoute = 0
      while (sequenceOfLevels.nonEmpty) {
        val currentLevel = sequenceOfLevels.head
        sequenceOfLevels = sequenceOfLevels.tail

        decorateAndAllocate(vehicle, positionInRoute, currentLevel, allocateFirst = true)
        positionInRoute += 1 << currentLevel
      }

      //      require(positionInRoute == (vehicleToPrecomputes(vehicle).length + 1), positionInRoute + " " + (vehicleToPrecomputes(vehicle).length + 1))

    }
    //printVehicleToPrecomputes(vehicle:Int)
  }

  private def identifyNodesAndAllocate(e:Option[IntSequenceExplorer],
                                       vehicle:Int,positionInVehicleRoute:Int,
                                       preComputedVals:Array[VehicleAndPosition]): Unit ={
    e match {
      case None =>
        //end
        vehicleToPrecomputes(vehicle) = Array.fill(positionInVehicleRoute+1)(null)
        vehicleToPrecomputes(vehicle)(positionInVehicleRoute) = new NodeAndPreComputes(vehicle)

      case  Some(x) if x.value < v && x.value != vehicle => ;
        //end
        vehicleToPrecomputes(vehicle) = Array.fill(positionInVehicleRoute+1)(null)
        vehicleToPrecomputes(vehicle)(positionInVehicleRoute) = new NodeAndPreComputes(vehicle)

      case Some(ex) =>
        preComputedVals(ex.value) = new VehicleAndPosition(vehicle, positionInVehicleRoute, node = ex.value)

        identifyNodesAndAllocate(ex.next, vehicle, positionInVehicleRoute + 1, preComputedVals)

        vehicleToPrecomputes(vehicle)(positionInVehicleRoute) = new NodeAndPreComputes(ex.value)
    }
  }

  private def decomposeToBitNumbersMSBFirst(x:Int):List[Int] = {
    require(x >= 0)

    var remaining = x
    var offset = 0
    var toReturn = List.empty[Int]

    while(remaining != 0){
      if((remaining & 1) != 0) {
        toReturn = offset :: toReturn
        remaining = remaining ^ 1
      }
      remaining = remaining >> 1
      offset = offset + 1
    }
    toReturn
  }

  private def decorateAndAllocate(vehicle:Int,positionInRoute:Int,level:Int,allocateFirst:Boolean){
    //println("decorateAndAllocate(vehicle:" + vehicle + " level:" + level + " positionInRoute:" + positionInRoute)

    if(allocateFirst){
      vehicleToPrecomputes(vehicle)(positionInRoute).precomputes = Array.fill(level+1)(null.asInstanceOf[T])
    }

    if(level == 0){
      val precompute = vehicleToPrecomputes(vehicle)(positionInRoute)
      val node = precompute.node

      if(node == vehicle && positionInRoute != 0){
        precompute.precomputes(0) = endNodeValue(node)
      }else{
        precompute.precomputes(0) = nodeValue(node)
      }

    }else{

      val stepSize = 1 << (level-1)

      decorateAndAllocate(vehicle,positionInRoute,level-1,allocateFirst = false)
      decorateAndAllocate(vehicle, positionInRoute+stepSize,level-1,allocateFirst=true)

      vehicleToPrecomputes(vehicle)(positionInRoute).precomputes(level) =
        composeSteps(
          vehicleToPrecomputes(vehicle)(positionInRoute).precomputes(level-1),
          vehicleToPrecomputes(vehicle)(positionInRoute + stepSize).precomputes(level-1))
    }
  }

  override def computeVehicleValue(vehicle:Int,
                                   segments:List[Segment[VehicleAndPosition]],
                                   routes:IntSequence,
                                   preComputedVals:Array[VehicleAndPosition]):U = {
   // println("routes:" + routes)
    computeVehicleValueComposed(vehicle, decorateSegments(vehicle, segments))
  }

  def decorateSegments(vehicle:Int,segments:List[Segment[VehicleAndPosition]]):List[LogReducedSegment[T]] = {

    segments match{
      case Nil =>
        //back to start; we add a single node (this will seldom be used, actually, since back to start is included in PreComputedSubSequence that was not flipped
        List(LogReducedPreComputedSubSequence[T](
          vehicle: Int, vehicle: Int,
          stepGenerator = () => List(endNodeValue(vehicle))))

      case head :: tail =>
        head match {
          case PreComputedSubSequence
            (startNode: Int, startNodeValue: VehicleAndPosition,
            endNode: Int, endNodeValue: VehicleAndPosition) =>

            if(tail.isEmpty
              && startNodeValue.vehicle == vehicle
              && endNodeValue.positionInVehicleRoute == vehicleToPrecomputes(vehicle).length-2){
              //last one, on the same vehicle as when pre-computation was performed, and nothing was removed until the end of this route
              List(LogReducedPreComputedSubSequence[T](
                startNode: Int, vehicle:Int, //we set vehicle as the real end
                stepGenerator = () => extractSequenceOfT(
                  startNodeValue.vehicle, startNodeValue.positionInVehicleRoute,
                  vehicleToPrecomputes(vehicle).length-1, flipped = false)))

            }else {
              LogReducedPreComputedSubSequence[T](
                startNode: Int, endNode: Int,
                stepGenerator = () => extractSequenceOfT(
                  startNodeValue.vehicle, startNodeValue.positionInVehicleRoute,
                  endNodeValue.positionInVehicleRoute, flipped = false)) :: decorateSegments(vehicle, tail)
            }
          case FlippedPreComputedSubSequence(
          startNode: Int, startNodeValue: VehicleAndPosition,
          endNode: Int, endNodeValue: VehicleAndPosition) =>

            LogReducedFlippedPreComputedSubSequence[T](
              startNode: Int, endNode: Int,
              stepGenerator = () => extractSequenceOfT(
                startNodeValue.vehicle, startNodeValue.positionInVehicleRoute,
                endNodeValue.positionInVehicleRoute, flipped = true))  :: decorateSegments(vehicle, tail)

          case NewNode(node: Int) =>
            LogReducedNewNode[T](node: Int, value = nodeValue(vehicle))  :: decorateSegments(vehicle, tail)
        }
    }
  }

  def extractSequenceOfT(vehicle:Int,
                         startPositionInRoute:Int,
                         endPositionInRoute:Int,
                         flipped:Boolean):List[T] = {

    if(flipped){
      extractSequenceOfTUnflippedGoingUp(vehicleToPrecomputes(vehicle),
        startPositionInRoute = endPositionInRoute,
        endPositionInRoute = startPositionInRoute)
    }else{
      extractSequenceOfTUnflippedGoingUp(vehicleToPrecomputes(vehicle),
        startPositionInRoute = startPositionInRoute,
        endPositionInRoute = endPositionInRoute)
    }
  }

  private def extractSequenceOfTUnflippedGoingUp(vehiclePreComputes:Array[NodeAndPreComputes],
                                                 startPositionInRoute:Int,
                                                 endPositionInRoute:Int):List[T] = {

    if(startPositionInRoute == endPositionInRoute+1) return List.empty

    val maxLevel = vehiclePreComputes(startPositionInRoute).precomputes.length - 1
    val levelStep = 1 << maxLevel


    if(startPositionInRoute + levelStep > endPositionInRoute+1){
      //we need to go down
      extractSequenceOfTUnflippedGoingDown(vehiclePreComputes:Array[NodeAndPreComputes],
        startPositionInRoute:Int,
        endPositionInRoute:Int,
        maxLevel-1)
    }else{
      //take the step and go up
      vehiclePreComputes(startPositionInRoute).precomputes(maxLevel) ::
        extractSequenceOfTUnflippedGoingUp(vehiclePreComputes:Array[NodeAndPreComputes],
          startPositionInRoute + levelStep,
          endPositionInRoute:Int)
    }
  }

  private def extractSequenceOfTUnflippedGoingDown(vehiclePreComputes:Array[NodeAndPreComputes],
                                                   startPositionInRoute:Int,
                                                   endPositionInRoute:Int,
                                                   maxLevel:Int):List[T] = {

    if(startPositionInRoute == endPositionInRoute+1) return List.empty

    val levelStep = 1 << maxLevel

    if(startPositionInRoute + levelStep > endPositionInRoute+1) {
      //too far, go down further
      extractSequenceOfTUnflippedGoingDown(vehiclePreComputes:Array[NodeAndPreComputes],
        startPositionInRoute:Int,
        endPositionInRoute:Int,
        maxLevel-1)
    }else{
      //take the step and go down
      vehiclePreComputes(startPositionInRoute).precomputes(maxLevel) ::
        extractSequenceOfTUnflippedGoingDown(vehiclePreComputes:Array[NodeAndPreComputes],
          startPositionInRoute + levelStep,
          endPositionInRoute:Int,
          maxLevel-1)
    }
  }
}


case class VehicleAndPosition(val vehicle:Int,
                              val positionInVehicleRoute:Int,
                              val node:Int)

