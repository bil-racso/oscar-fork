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

import oscar.cbls.algo.accList.AccList
import oscar.cbls.algo.fun._
import oscar.cbls.algo.seq.IntSequence

/**
  * All the functions implemented here are bijections
  * they map positions in new sequence to positions in old sequence
  */
abstract class FunctionForPreCompute {

  val fun: PiecewiseLinearFun
  val externalPositionOfLastRoutedNode: Int

  def concreteFunction:ConcreteFunctionForPreCompute

  def kindOfComputation(fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep]

  def stackInsert(value: Int, pos: Int): FunctionForPreCompute = new InsertStackedFunction(concreteFunction, value, pos)

  def stackDelete(pos: Int): FunctionForPreCompute = new DeleteStackedFunction(concreteFunction, pos)

  def stackMove(startPositionIncluded: Int,
                endPositionIncluded: Int,
                moveAfterPosition: Int,
                flip: Boolean): FunctionForPreCompute =
    new MoveStackedFunction(concreteFunction, startPositionIncluded, endPositionIncluded, moveAfterPosition, flip)
}

/**
  * @author Quentin Meurisse
  */
object ConcreteFunctionForPreCompute {

  /**
    * Initialize the bijection with the identity
    * @param seq
    * @return
    */
  def apply(seq: IntSequence): ConcreteFunctionForPreCompute ={
    val bij = PiecewiseLinearFun.identity
    val lastRoutedNodePos = seq.size-1
    new ConcreteFunctionForPreCompute(bij, lastRoutedNodePos, lastRoutedNodePos, lastRoutedNodePos)
  }
}

/**
  * Bijection from external positions to internal positions. We suppose that the bijection at checkpoint is the identity.
  * For values <= maxValueWithPreCompute, we have pre-compute, for the others,  we need from scratch procedure
  * @param fun
  * @param externalPositionOfLastRoutedNode
  * @param externalPositionOfLastRemovedNodeWithPreCompute the interval ]internalPositionOfLastRoutedNode, externalPositionOfLastRemovedNode]
  *                                                        contains the removed node with pre-compute
  * @param maxValueWithPreCompute
  */

class ConcreteFunctionForPreCompute(val fun: PiecewiseLinearFun,
                                    val externalPositionOfLastRoutedNode: Int,
                                    val externalPositionOfLastRemovedNodeWithPreCompute: Int,
                                    val maxValueWithPreCompute: Int)
  extends FunctionForPreCompute{

  val concreteFunction: ConcreteFunctionForPreCompute = this

  override def kindOfComputation(fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep] = {

    val pivotOfFromIncluded =
      fun.pivotApplyingTo(fromPosIncluded) match {
        case None => new Pivot(0, LinearTransform.identity)
        case Some(p) => p
      }
    val pivotOfToIncluded = fun.pivotApplyingTo(toPosIncluded) match {
      case None => new Pivot(0, LinearTransform.identity)
      case Some(p) => p
    }

    if (pivotOfFromIncluded.fromValue == pivotOfToIncluded.fromValue) {
      kindOfComputationOnSegment(pivotOfFromIncluded, fromPosIncluded, toPosIncluded)
    } else {
      var computationSteps:AccList[ComputationStep] = AccList.empty()

      var prevPivot = pivotOfFromIncluded
      var pivotExplorer = fun.pivotWithPositionAfter(fromPosIncluded).get
      if(pivotExplorer.value.fromValue == prevPivot.fromValue)
        pivotExplorer = pivotExplorer.next.get

      while({
        val prevPivotValue = prevPivot.fromValue
        val currentPivotValue = pivotExplorer.value.fromValue

        if(prevPivotValue == pivotOfFromIncluded.fromValue)
          computationSteps = AccList.acc(computationSteps,kindOfComputationOnSegment(prevPivot, fromPosIncluded, currentPivotValue - 1))
        else
          computationSteps = AccList.acc(computationSteps,kindOfComputationOnSegment(prevPivot, prevPivotValue, currentPivotValue - 1))

        if (currentPivotValue == pivotOfToIncluded.fromValue) {
          computationSteps = AccList.acc(computationSteps,kindOfComputationOnSegment(pivotExplorer.value, currentPivotValue, toPosIncluded))
          false
        }
        else {
          prevPivot = pivotExplorer.value
          pivotExplorer = pivotExplorer.next.get
          true
        }
      }) {}
      computationSteps
    }
  }

  private def kindOfComputationOnSegment(pivot: Pivot, fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep] = {
    require(0 <= fromPosIncluded && fromPosIncluded <= externalPositionOfLastRoutedNode,
      "fromPositionInclude(= " + fromPosIncluded + ") should be in [0, externalPositionOfLastRoutedNode(= " + externalPositionOfLastRoutedNode + ")]")

    require(0 <= toPosIncluded && toPosIncluded <= externalPositionOfLastRoutedNode,
      "toPositionInclude(= " + toPosIncluded + ") should be in [0, externalPositionOfLastRoutedNode(= " + externalPositionOfLastRoutedNode + ")]")

    require(fromPosIncluded <= toPosIncluded,
      "fromPositionInclude(= " + fromPosIncluded + ") should be <= toPosIncluded(= " +  toPosIncluded + ")")


    val fromValue = fun(fromPosIncluded)
    val toValue  = fun(toPosIncluded)

    //generating the computation steps
    if (!pivot.f.minus) {
      if (toValue <= maxValueWithPreCompute)
        AccList.base(FetchFromPreCompute(fromPosIncluded, toPosIncluded,false))
      else if (fromValue > maxValueWithPreCompute)
        AccList.base(FromScratch(fromPosIncluded, toPosIncluded))
      else {
        val posMaxValueWithPreCompute = fromPosIncluded + (maxValueWithPreCompute - fromValue)
        AccList.base(FetchFromPreCompute(fromPosIncluded, posMaxValueWithPreCompute, false),
          FromScratch(posMaxValueWithPreCompute + 1, toPosIncluded))
      }
    }
    else {
      if (fromValue <= maxValueWithPreCompute)
        AccList.base(FetchFromPreCompute(fromPosIncluded, toPosIncluded, true))
      else if (toValue > maxValueWithPreCompute)
        AccList.base(FromScratch(fromPosIncluded, toPosIncluded))
      else {
        val posMaxValueWithPreCompute = toPosIncluded - (maxValueWithPreCompute - toPosIncluded)
        AccList.base(FromScratch(fromPosIncluded, posMaxValueWithPreCompute - 1),
          FetchFromPreCompute(posMaxValueWithPreCompute, toPosIncluded, true))
      }
    }
  }
}

class InsertStackedFunction(base: ConcreteFunctionForPreCompute, value: Int, pos: Int) extends FunctionForPreCompute {

  val fun: PiecewiseLinearFun = base.fun
  val externalPositionOfLastRoutedNode : Int = base.externalPositionOfLastRoutedNode + 1

  lazy val concreteFunction: ConcreteFunctionForPreCompute = {
    val funForPreCompute = base.concreteFunction
    val fun = funForPreCompute.fun
    val maxValueWithPreCompute = funForPreCompute.maxValueWithPreCompute
    val externalPositionOfLastRoutedNode = funForPreCompute.externalPositionOfLastRoutedNode
    val externalPositionOfLastRemovedNode = funForPreCompute.externalPositionOfLastRemovedNodeWithPreCompute

    val size = externalPositionOfLastRemovedNode + 1
    val updatedFun =
      if (pos == size) {
        //inserting at end of the sequence
        fun.updateForCompositionBefore(
          (size, size, LinearTransform (externalPositionOfLastRemovedNode + 1 - pos, false) ) )
        //TODO: this might be always identity, actually, so useless!
      }
      else {
        //inserting somewhere within the sequence, need to shift upper part
        fun.swapAdjacentZonesShiftFirst (pos, size - 1, size, false)
      }

    new ConcreteFunctionForPreCompute(updatedFun,
      externalPositionOfLastRoutedNode + 1,
      externalPositionOfLastRemovedNode + 1,
      maxValueWithPreCompute)
  }


  override def kindOfComputation(fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep] = {
    val fromPosBeforeInsert =
      if (fromPosIncluded < pos) fromPosIncluded
      else fromPosIncluded - 1

    val toPosBeforeInsert =
      if(toPosIncluded < pos) toPosIncluded
      else toPosIncluded - 1

    if(fromPosIncluded < pos && toPosIncluded >= pos){
      val stepBeforePos = base.kindOfComputation(fromPosBeforeInsert, pos-1)
      val stepAfterInsert:AccList[ComputationStep] =
        if (toPosIncluded == pos) AccList.empty()
        else base.kindOfComputation(pos, toPosBeforeInsert)
      AccList.acc(stepBeforePos,AccList.base(FromScratch(pos, pos, topOfStack = true)),stepAfterInsert)
    }
    else
      base.kindOfComputation(fromPosBeforeInsert, toPosBeforeInsert)
  }
}


class DeleteStackedFunction(base: ConcreteFunctionForPreCompute, pos: Int) extends FunctionForPreCompute{

  val fun: PiecewiseLinearFun = base.fun
  val externalPositionOfLastRoutedNode: Int = base.externalPositionOfLastRoutedNode -1

  lazy val concreteFunction: ConcreteFunctionForPreCompute = {
    val funForPreCompute = base.concreteFunction
    val fun = funForPreCompute.fun
    val externalPositionOfLastRoutedNode = funForPreCompute.externalPositionOfLastRoutedNode
    val maxValueWithPreCompute = funForPreCompute.maxValueWithPreCompute
    val externalPosOfLastRemovedNode = funForPreCompute.externalPositionOfLastRemovedNodeWithPreCompute

    val removedValue = fun(pos)

    val updatedBij = fun.swapAdjacentZonesShiftFirst(pos, pos, externalPosOfLastRemovedNode, false)

    val newExternalPosOfLastRemovedNode =
      if (removedValue <= maxValueWithPreCompute) externalPosOfLastRemovedNode
      else {
        externalPosOfLastRemovedNode - 1
      }

    new ConcreteFunctionForPreCompute(updatedBij,
      externalPositionOfLastRoutedNode - 1,
      newExternalPosOfLastRemovedNode,
      maxValueWithPreCompute)
  }

  override def kindOfComputation(fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep] = {
    val fromPosBeforeDelete =
      if (fromPosIncluded < pos) fromPosIncluded
      else fromPosIncluded + 1

    val toPosBeforeDelete =
      if (toPosIncluded < pos) toPosIncluded
      else toPosIncluded +1

    if (fromPosBeforeDelete < pos && pos <= toPosBeforeDelete){
      //deleted position falls within requested interval
      val stepsBeforePos = base.kindOfComputation(fromPosBeforeDelete, pos-1)
      val stepsAfterPos:AccList[ComputationStep] =
        if (toPosBeforeDelete == pos) AccList.empty()
        else base.kindOfComputation(pos+1, toPosBeforeDelete)

      AccList.acc(stepsBeforePos,stepsAfterPos)
    }
    else
      base.kindOfComputation(fromPosBeforeDelete, toPosBeforeDelete)
  }

}

class MoveStackedFunction(base: ConcreteFunctionForPreCompute,
                          startPositionIncluded: Int,
                          endPositionIncluded: Int,
                          moveAfterPosition: Int,
                          flip: Boolean) extends FunctionForPreCompute{

  val fun: PiecewiseLinearFun = base.fun
  val externalPositionOfLastRoutedNode: Int = base.externalPositionOfLastRoutedNode

  lazy val concreteFunction: ConcreteFunctionForPreCompute = {
    val functionForPreCompute = base.concreteFunction
    val fun = functionForPreCompute.fun
    val externalPositionOfLastRoutedNode = functionForPreCompute.externalPositionOfLastRoutedNode
    val externalPositionOfLastRemovedNode = functionForPreCompute.externalPositionOfLastRemovedNodeWithPreCompute
    val maxValueWithPreCompute = functionForPreCompute.maxValueWithPreCompute

    val size = externalPositionOfLastRoutedNode + 1

    require(startPositionIncluded >= 0 && startPositionIncluded < size, "startPositionIncluded should be in [0,size[")
    require(endPositionIncluded >= 0 && endPositionIncluded < size, "endPositionIncluded(=" + endPositionIncluded+ ") should be in [0,size(="+size+")[ ")
    require(moveAfterPosition >= -1 && moveAfterPosition < size, "moveAfterPosition=" + moveAfterPosition + " should be in [-1,size=" + size+"[ ")

    require(
      moveAfterPosition < startPositionIncluded || moveAfterPosition > endPositionIncluded,
      "moveAfterPosition=" + moveAfterPosition + " cannot be between startPositionIncluded=" + startPositionIncluded + " and endPositionIncluded=" + endPositionIncluded)
    require(startPositionIncluded <= endPositionIncluded, "startPositionIncluded=" + startPositionIncluded + " should be <= endPositionIncluded=" + endPositionIncluded)


    if (moveAfterPosition + 1 == startPositionIncluded) {
      //not moving
      if (flip) {
        //just flipping
        val updatedFun = fun.updateForCompositionBefore(
          (startPositionIncluded, endPositionIncluded, LinearTransform(endPositionIncluded + startPositionIncluded, true)))

        new ConcreteFunctionForPreCompute(updatedFun,
          externalPositionOfLastRoutedNode,
          externalPositionOfLastRemovedNode,
          maxValueWithPreCompute)
      }else {
        functionForPreCompute // nop
      }
    }
    else {
      if (moveAfterPosition > startPositionIncluded) {
        //move upwards
        val updatedFun =
          if(!flip) {
            fun.swapAdjacentZonesShiftBest(
              startPositionIncluded,
              endPositionIncluded,
              moveAfterPosition)

          }else {
            fun.swapAdjacentZonesShiftSecond(
              startPositionIncluded,
              endPositionIncluded,
              moveAfterPosition: Int,
              true)
          }

        new ConcreteFunctionForPreCompute(updatedFun,
          externalPositionOfLastRoutedNode,
          externalPositionOfLastRemovedNode,
          maxValueWithPreCompute)
      }
      else {
        //move downwards
        val updatedFun =
          if(!flip) {
            fun.swapAdjacentZonesShiftBest(
              moveAfterPosition+1,
              startPositionIncluded-1,
              endPositionIncluded)
          }else {
            fun.swapAdjacentZonesShiftFirst(
              moveAfterPosition+1,
              startPositionIncluded-1,
              endPositionIncluded,true)
          }

        new ConcreteFunctionForPreCompute(updatedFun,
          externalPositionOfLastRoutedNode,
          externalPositionOfLastRemovedNode,
          maxValueWithPreCompute)
      }
    }
  }

  override def kindOfComputation(fromPosIncluded: Int, toPosIncluded: Int): AccList[ComputationStep] = {
    // we suppose that fromPosIncluded is the starting point of a vehicle and toPosIncluded the last point of this vehicle
    if (moveAfterPosition + 1 == startPositionIncluded){
      if (flip){
        // simple flip

        if(fromPosIncluded < startPositionIncluded && toPosIncluded >= endPositionIncluded) {
          // we are at the vehicle of the flipped segment

          val stepsBeforeFlip = base.kindOfComputation(fromPosIncluded, moveAfterPosition)

          val flippedSteps = flipListOfSteps(base.kindOfComputation(startPositionIncluded, endPositionIncluded).toList)

          val stepsAfterFlip:AccList[ComputationStep] =
            if (toPosIncluded == endPositionIncluded) AccList.empty()
            else base.kindOfComputation(endPositionIncluded + 1, toPosIncluded)

          AccList.acc(stepsBeforeFlip,AccList.fromList(flippedSteps),stepsAfterFlip)
        }else {
          // we are looking for another a vehicle
          base.kindOfComputation(fromPosIncluded, toPosIncluded)
        }
      }else {
        // nop
        base.kindOfComputation(fromPosIncluded, toPosIncluded)
      }
    }
    else{
      val movedSegSize = endPositionIncluded - startPositionIncluded +1

      if(moveAfterPosition > startPositionIncluded){
        // move upwards

        if(toPosIncluded < startPositionIncluded || fromPosIncluded > moveAfterPosition) {
          // vehicle which is not impacted by the move
          // also valid if the source vehicle is reduced to startin point of the vehicle
          base.kindOfComputation(fromPosIncluded, toPosIncluded)

        }else if(fromPosIncluded < startPositionIncluded && toPosIncluded >= moveAfterPosition){
          // move on same vehicle. Wa are at the vehicle of the move
          val stepBeforeMove = base.kindOfComputation(fromPosIncluded, startPositionIncluded - 1)

          val stepOnMovedSegment:AccList[ComputationStep] =
            if(flip) AccList.fromList(flipListOfSteps(base.kindOfComputation(startPositionIncluded, endPositionIncluded).toList))
            else base.kindOfComputation(startPositionIncluded, endPositionIncluded)

          val stepOnSegOfAfterPos = base.kindOfComputation(endPositionIncluded+1, moveAfterPosition)

          val stepAfterMove:AccList[ComputationStep] =
            if(toPosIncluded == moveAfterPosition) AccList.empty()
            else base.kindOfComputation(moveAfterPosition+1, toPosIncluded)

          AccList.acc(stepBeforeMove,stepOnSegOfAfterPos, stepOnMovedSegment, stepAfterMove)
        } else if (movedSegSize + fromPosIncluded <= moveAfterPosition && toPosIncluded >= moveAfterPosition){
          // move on different vehicles. We are at the target vehicle
          val movedStep =
            if (flip) AccList.fromList(flipListOfSteps(base.kindOfComputation(startPositionIncluded, endPositionIncluded).toList))
            else base.kindOfComputation(startPositionIncluded, endPositionIncluded)

          val stepBeforeMove = base.kindOfComputation(fromPosIncluded + movedSegSize, moveAfterPosition)
          val stepAfterMove:AccList[ComputationStep] =
            if(toPosIncluded != moveAfterPosition) base.kindOfComputation(moveAfterPosition+1, toPosIncluded)
            else AccList.empty()

          AccList.acc(stepBeforeMove,movedStep,stepAfterMove)
        } else if (fromPosIncluded < startPositionIncluded && toPosIncluded + movedSegSize >= endPositionIncluded){
          // move on different vehicles. We are at the source vehicle

          val stepBeforeMove = base.kindOfComputation(fromPosIncluded, startPositionIncluded -1)
          val stepAfterMove = base.kindOfComputation(endPositionIncluded+1, toPosIncluded + movedSegSize)

          AccList.acc(stepBeforeMove,stepAfterMove)
        }else {
          // move on different vehicles. We are on a vehicle positioning between the source vehicle and the target vehicle
          base.kindOfComputation(fromPosIncluded + movedSegSize, toPosIncluded + movedSegSize)
        }
      } else{
        // move downwards
        if (fromPosIncluded > endPositionIncluded || toPosIncluded < moveAfterPosition) {
          // vehicle which is not impacted by the move
          base.kindOfComputation(fromPosIncluded, toPosIncluded)

        }else if(fromPosIncluded <= moveAfterPosition && toPosIncluded >= endPositionIncluded){
          // we are looking at the vehicle of movement
          val stepBeforeMove = base.kindOfComputation(fromPosIncluded, moveAfterPosition)
          val stepOnSegmentOfAfter = base.kindOfComputation(moveAfterPosition+1, startPositionIncluded-1)
          val stepOnMovedSegment =
            if(flip) AccList.fromList(flipListOfSteps(base.kindOfComputation(startPositionIncluded, endPositionIncluded).toList))
            else base.kindOfComputation(startPositionIncluded, endPositionIncluded)
          val stepAfterMove:AccList[ComputationStep] =
            if(toPosIncluded == endPositionIncluded)AccList.empty()
            else base.kindOfComputation(endPositionIncluded+1, toPosIncluded)

          AccList.acc(stepBeforeMove,stepOnMovedSegment,stepOnSegmentOfAfter,stepAfterMove)
        } else if (fromPosIncluded <= moveAfterPosition && toPosIncluded - movedSegSize  >= moveAfterPosition){
          // move on different vehicles. We are at the target vehicle

          val stepBeforeMove = base.kindOfComputation(fromPosIncluded, moveAfterPosition)
          val stepOnMovedSegment =
            if(flip) AccList.fromList(flipListOfSteps(base.kindOfComputation(startPositionIncluded, endPositionIncluded).toList))
            else base.kindOfComputation(startPositionIncluded, endPositionIncluded)

          val stepAfterMove:AccList[ComputationStep] =
            if(toPosIncluded - movedSegSize == moveAfterPosition) AccList.empty()
            else base.kindOfComputation(moveAfterPosition+1, toPosIncluded - movedSegSize)

          AccList.acc(stepBeforeMove,stepOnMovedSegment,stepAfterMove)
        }else if (fromPosIncluded - movedSegSize < startPositionIncluded && toPosIncluded >= endPositionIncluded){
          // move on different vehicle. We are at the source vehicle

          val stepBeforeMove = base.kindOfComputation(fromPosIncluded - movedSegSize, startPositionIncluded-1)
          val stepAfterMove:AccList[ComputationStep] =
            if(toPosIncluded == endPositionIncluded) AccList.empty()
            else base.kindOfComputation(endPositionIncluded + 1, toPosIncluded)

          AccList.acc(stepBeforeMove,stepAfterMove)
        }else {
          // move on different vehicle. We are on vehicle positioning between the target vehicle and the source vehicle
          base.kindOfComputation(fromPosIncluded - movedSegSize, toPosIncluded - movedSegSize)
        }
      }
    }
  }

  private def flipListOfSteps(segList: List[ComputationStep]): List[ComputationStep] = {
    segList.map(_.reverse()).reverse
  }
}