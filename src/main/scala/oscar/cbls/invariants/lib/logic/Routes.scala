package oscar.cbls.invariants.lib.logic

/*******************************************************************************
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
 ******************************************************************************/

/*******************************************************************************
 * Contributors:
 *     This code has been initially developed by CETIC www.cetic.be
 *         by Renaud De Landtsheer
 ******************************************************************************/

import oscar.cbls.invariants.core.computation._
import oscar.cbls.invariants.core.algo.heap.BinomialHeap

/**this invariants maintains data structures representing vrp of vehicles.
 * for use in TSP, VRP, etc.
 * arrays start at one until N
 * position 0 is to denote an unrouted node.
 * The nodes from 1 to V are the starting points of vehicles.
 *
 * @param V the number of vrp to consider V>=1 and V<=N
 */
case class Routes(V: Int,
                  Next:Array[IntVar],
                  PositionInRoute:Array[IntVar],
                  RouteNr:Array[IntVar]) extends Invariant {

  val ArrayOfUnregisterKeys = registerStaticAndDynamicDependencyArrayIndex(Next)
  finishInitialization()
  for(v <- PositionInRoute){v.setDefiningInvariant(this)}
  for(v <- RouteNr){v.setDefiningInvariant(this)}

  for (v <- 1 to V) DecorateVehicleRoute(V)
  PositionInRoute(0) := 0
  RouteNr(0) := 0

  def DecorateVehicleRoute(V:Int){
    var currentID = Next(V).value
    var currentPosition = 1
    PositionInRoute(V) := 0
    RouteNr(V) := V
    while(currentID !=V){
      assert(currentID>V)
      PositionInRoute(currentID) := currentPosition
      RouteNr(currentID) := V
      currentID = Next(currentID).value
      currentPosition +=1
    }
  }

  var ToUpdate:List[Int] = List.empty
  var ToUpdateCount:Int = 0

  override def notifyIntChanged(v: IntVar, i: Int, OldVal: Int, NewVal: Int){
    unregisterDynamicallyListenedElement(ArrayOfUnregisterKeys(i))
    ArrayOfUnregisterKeys(i) = null
    ToUpdate = i :: ToUpdate
    ToUpdateCount +=1
    scheduleForPropagation()
  }

  @inline
  final def isUpToDate(node:Int):Boolean = {
    ((RouteNr(node).getValue(true) == RouteNr(Next(node).value).getValue(true))
      && (PositionInRoute(node).getValue(true) + 1 == PositionInRoute(Next(node).value).getValue(true)))
  }

  override def performPropagation(){
    //le numéro de noeud, son ancienne position dans le circuit
    val heap = new BinomialHeap[(Int,Int)]((a:(Int,Int)) => a._2, ToUpdateCount)
    for (node <- ToUpdate){
      if(Next(node).value == 0){
        //node is unrouted now
        RouteNr(node) := 0
        PositionInRoute(node) := 0
        ArrayOfUnregisterKeys(node) = registerDynamicallyListenedElement(Next(node),node)
      }else if(isUpToDate(node)){
        ArrayOfUnregisterKeys(node) = registerDynamicallyListenedElement(Next(node),node)
      }else{
        heap.insert((node,PositionInRoute(node).getValue(true)))
      }
    }
    ToUpdate = List.empty
    ToUpdateCount = 0

    while(!heap.isEmpty){
      val currentNodeForUpdate = heap.popFirst()._1
      DecorateRouteStartingFromAndUntilConformOrEnd(currentNodeForUpdate)
      ArrayOfUnregisterKeys(currentNodeForUpdate)
        = registerDynamicallyListenedElement(Next(currentNodeForUpdate),currentNodeForUpdate)
    }
  }

  /**
   * @param nodeID est le noeud dont on a changé le next.
   */ //TODO: there is a bug somewhere, this does sometime get into a cycle.
  def DecorateRouteStartingFromAndUntilConformOrEnd(nodeID:Int){
    var currentNode = nodeID
    while(!isUpToDate(currentNode) && Next(currentNode).value > V){
      val nextID = Next(currentNode).value
      PositionInRoute(nextID) := (PositionInRoute(currentNode).getValue(true)+ 1)
      RouteNr(nextID) := RouteNr(currentNode).getValue(true)
      currentNode = nextID
    }
  }

  override def checkInternals(){
    for(n <- Next.indices){
      val next = Next(n).value
      if (next !=0 && n!=0){
        assert(RouteNr(next).value == RouteNr(n).value)
        assert(PositionInRoute(next).value == PositionInRoute(n).value +1)
      }else{
        assert(RouteNr(n) ==0)
        assert(PositionInRoute(n) == 0)
      }
      if(n <=V && n!=0){
        assert(RouteNr(n).value == n)
        assert(PositionInRoute(n).value == 0)
      }
    }
  }
}

object Routes{
  def buildRoutes(Next:Array[IntVar], V:Int):Routes = {
    val m:Model = InvariantHelper.FindModel(Next)
    val PositionInRoute = Array.tabulate(Next.size)(i => new IntVar(m, 0, V, 0, "PositionInRouteOfPt" + i))
    val RouteNr = Array.tabulate(Next.size)(i => new IntVar(m, 0, V, 0, "RouteNrOfPt" + i))

    Routes(V, Next, PositionInRoute, RouteNr)
  }
}
