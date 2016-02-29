package oscar.examples.cbls.routing.visual.FunctionGraphic

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

import java.awt._
import java.awt.geom.Line2D.Double
import java.awt.geom.Rectangle2D

import oscar.cbls.search.StopWatch
import oscar.visual.VisualDrawing
import oscar.visual.shapes.{VisualLine, VisualText}

import scala.collection.mutable.ListBuffer

/** This abstract class represent the base structure for
  * the classes who'll have the purpose of drawing the curve of the objective function.
  * It has many variables :
  * minWidth : it represents the left horizontal drawing limit of the curve
  * minHeight : it represents the top vertical drawing limit of the curve
  * maxWidth : it represents the right horizontal drawing limit of the curve
  * maxHeight : it represents the bottom vertical drawing limit of the curve
  * objValues : a ListBuffer that contains all the objective value encountered so far
  * objTimes : a ListBuffer that contains all the time at which the objective value as been encountered
  * objBestValues : a ListBuffer that contains the best objective value encountered so far for each objTimes(i)
  * minObjTime : it contains the min X value that will be drawn on the graphic
  * maxObjTime : it contains the max X value that will be drawn on the graphic
  * maxNumberOfObj : it contains the max number of object that could drawn on the graphic
  * best : it contains the best value encountered so far
  *
  * @author fabian.germeau@student.vinci.be
  */

abstract class FunctionGraphic() extends VisualDrawing(false,false) with StopWatch{


  val minWidth = 70
  val minHeight = 10
  var maxHeight = Int.MaxValue
  var maxWidth = Int.MaxValue
  val yValues:ListBuffer[Int] = new ListBuffer[Int]
  val xValues:ListBuffer[Long] = new ListBuffer[Long]
  val bestValues:ListBuffer[Int] = new ListBuffer[Int]
  val xColorValues:ListBuffer[Color] = new ListBuffer[Color]
  var minXValueDisplayed:Long = 0
  var maxXValueDisplayed:Long = 0
  var minYValueDisplayed:Long = 0
  var maxYValueDisplayed:Long = 0
  var maxXValue:Long = 0
  var maxTimeValue:Long = 0
  var maxNumberOfXYValues:Int = 0
  var timeUnit:scala.Double = 1
  var heightAdapter:scala.Double = 1
  val best:Int ={
    if(bestValues.nonEmpty)
      bestValues.min
    else
      Int.MaxValue
  }

  setLayout(new BorderLayout())

  def notifyNewObjectiveValue(objValue:Int, objTime:Long, color:Color)

  def clear(): Unit ={
    maxHeight = getHeight - 30
    maxWidth = getWidth - 10
    super.clear()
  }

  def drawGlobalCurve()

  def drawObjectiveCurve()

  def setTimeBorders(position:Int){}

  def setMaxNumberOfObject(percentage:scala.Double){}


}

/** This class has the purpose to draw the objective function curve.
  * It firstly collect all the objective value.
  * To do so, the search procedure has to call the notifyNewObjectiveValue with the objective value and the current time.
  * After that it draws the entire curve with the drawObjectiveCurve method
  *
  * @author fabian.germeau@student.vinci.be
  */

class ObjFunctionGraphic(width:Int,height:Int) extends FunctionGraphic(){

  /**
    * Clear the graphic and reset the different ListBuffers in order to begin another research
    */
  override def clear(): Unit ={
    super.clear()
    yValues.clear()
    xValues.clear()
    bestValues.clear()
  }

  /**
    * Save the objective value, the best value encountered so far and the time value of the current state
    */
  def notifyNewObjectiveValue(objValue:Int, time:Long, color:Color): Unit ={
    xValues.append(time)
    yValues.append(objValue)
    bestValues.append(Math.min(best,objValue))
    xColorValues.append(color)
    maxXValueDisplayed = time
    maxYValueDisplayed = best
    maxNumberOfXYValues = yValues.length
  }

  /**
    * Prepare the different values needed to draw a portion of the global curve.
    * It uses the usedList generated by the getUsedLists method.
    * It begins by locating the maxObjValue and the minObjValue in order to adapt the scale of the axis.
    * It gets the border's values for the X axis and the Y axis and then call the drawAxis and drawCurve method.
    */
  def drawObjectiveCurve() = {
    super.clear()

    val (usedObjValues,usedObjTimes,usedObjBestValues,usedColorValues) = getUsedLists

    val maxObjValue = usedObjValues.max
    val minObjValue = Math.min(usedObjValues.min,best)
    val diffObjValue = maxObjValue - minObjValue

    val maxTimeValue = usedObjTimes.max
    val minTimeValue = usedObjTimes.min
    val diffTimeValue = maxTimeValue - minTimeValue

    val (bottom,top) = getOrdFloorValues(diffObjValue,minObjValue,maxObjValue)
    val (left,right) = getAbsFloorValues(diffTimeValue,minTimeValue,maxTimeValue)

    drawAxis(bottom,top,left,right)
    drawCurve(usedObjValues,usedObjTimes,usedObjBestValues,usedColorValues,bottom,top)
  }

  /**
    * Prepare the different values needed to draw the global curve.
    * It's similar to the previous method but this time the goal is to draw the entire curve.
    */
  override def drawGlobalCurve() = {
    super.clear()
    maxNumberOfXYValues = yValues.length
    val diffObjValue = maxYValueDisplayed - minYValueDisplayed

    val diffTimeValue = maxXValueDisplayed - minXValueDisplayed

    println("diff : ", diffObjValue, diffTimeValue)
    val (bottom,top) = getOrdFloorValues(diffObjValue,minYValueDisplayed,maxYValueDisplayed)
    val (left,right) = getAbsFloorValues(diffTimeValue,minXValueDisplayed,minYValueDisplayed)

    println("borders : ",bottom, top, left, right)

    drawAxis(bottom,top,left,right)
    drawCurve(bottom,top)
  }

  /**
    * Draw the global curve, including all the values
 *
    * @param minOrdValue the bottom border of the Y axis (needed to adapt the objValue in pixel)
    * @param maxOrdValue the top border of the Y axis (needed to adapt the objValue in pixel)
    */
  def drawCurve(minOrdValue:Long,maxOrdValue:Long): Unit = {
    var currentTimeUnit:scala.Double = -minXValueDisplayed/timeUnit
    var currentTimeUnitValue:scala.Double = 0
    var currentTimeUnitValuesNumber:scala.Double = 0.0
    var previousTimeUnitValue:scala.Double = 0.0
    var previousTimeUnit:scala.Double = 0.0
    println(heightAdapter, timeUnit)
    for(i <- yValues.indices){
      if((xValues(i)/timeUnit).toInt == currentTimeUnit){
        currentTimeUnitValue += yValues(i)/heightAdapter
        currentTimeUnitValuesNumber += 1
      }else{
        if(currentTimeUnitValuesNumber != 0) {
          //currentTimeUnitValue = adjustHeight(currentTimeUnitValue/currentTimeUnitValuesNumber,minOrdValue,maxOrdValue)
          currentTimeUnitValue = maxHeight - (currentTimeUnitValue/currentTimeUnitValuesNumber)-minYValueDisplayed/heightAdapter
          if(previousTimeUnitValue == 0)
            previousTimeUnitValue = currentTimeUnitValue
          val line = new VisualLine(this, new Double(previousTimeUnit+70, previousTimeUnitValue, currentTimeUnit+70, currentTimeUnitValue))
          line.outerCol_$eq(xColorValues(i))
          previousTimeUnit = currentTimeUnit
          previousTimeUnitValue = currentTimeUnitValue
          currentTimeUnitValue = 0
          currentTimeUnitValuesNumber = 0
        }
        while(currentTimeUnit < (xValues(i)/timeUnit).toInt){
          //println(currentTimeUnit, xValues(i)/timeUnit)
          currentTimeUnit += 1
        }
      }
    }

    //new VisualLine(this,new Double(previousTimeUnit+70, previousTimeUnitValue, maxWidth, previousTimeUnitValue))
  }

  /**
    * Draw a part of the curve
 *
    * @param usedObjValues the list of the objValues that will be drawn
    * @param usedObjTimes the list of the objTimes that will be drawn
    * @param usedObjBestValues the list of the objBestValues that will be drawn
    * @param minOrdValue the bottom border of the Y axis (needed to adapt the objValue in pixel)
    * @param maxOrdValue the top border of the Y axis (needed to adapt the objValue in pixel)
    */
  def drawCurve(usedObjValues:scala.List[Int], usedObjTimes:scala.List[Long], usedObjBestValues:scala.List[Int], usedColorValues:scala.List[Color], minOrdValue:Long,maxOrdValue:Long): Unit ={
    var (prevX, prevY) = (Int.MaxValue,Int.MaxValue)
    var prevBest:Int = 0
    for(i <- usedObjValues.indices) {
      val x = adjustWidth(usedObjTimes(i),minXValueDisplayed,maxXValueDisplayed)
      val y = adjustHeight(usedObjValues(i), minOrdValue, maxOrdValue)
      val (posX,posY) = (x,y)
      val yBest = adjustHeight(usedObjBestValues(i), minOrdValue, maxOrdValue)
      if(prevX != Int.MaxValue){
        val realLine = new VisualLine(this,new Double(prevX,prevY,posX,posY))
        realLine.outerCol_$eq(usedColorValues(i))
        val bestLine = new VisualLine(this,new Double(prevX,prevBest,posX,yBest))
        bestLine.outerCol_$eq(new Color(102,205,0))
        bestLine.dashed_=(true)
      }
      prevX = posX
      prevY = posY
      prevBest = yBest
    }
  }

  /**
    * Adjust the value of an objValue to the size of the window
 *
    * @param value The value that has to be adjusted
    * @param minOrdValue the bottom border of the Y axis
    * @param maxOrdValue the top border of the Y axis
    * @return The adjusted value
    */
  def adjustHeight(value:scala.Double, minOrdValue:Long, maxOrdValue:Long): Int ={
    (maxHeight - ((maxHeight-minHeight) * (value - minOrdValue)/Math.max(maxOrdValue - minOrdValue,1))).toInt
  }

  /**
    * Adjust the time of occurrence of an objectif to the size of the window
 *
    * @param value The value that has to be adjusted
    * @param minAbsValue the left border of the X axis
    * @param maxAbsValue the right border of the X axis
    * @return The adjusted value
    */
  def adjustWidth(value:scala.Double, minAbsValue:scala.Double, maxAbsValue:scala.Double): Int ={
    (minWidth + (maxWidth - minWidth) * (value - minAbsValue)/Math.max(maxAbsValue - minAbsValue,1)).toInt
  }

  /**
    * Return the values of the Y axis's border
 *
    * @param diffObjValue The difference between the minObjValue and the maxObjValue
    * @param minObjValue The minimum objective value that will be drawn
    * @param maxObjValue The maximum objective value that will be drawn
    * @return The bottom and top border of the Y axis
    */
  def getOrdFloorValues(diffObjValue:Long, minObjValue:Long, maxObjValue:Long): (Long,Long) ={
    if(diffObjValue == 0){
      (minObjValue - 5, minObjValue + 5)
    }else {
      var diffFloor:Long = 1
      while (diffFloor <= diffObjValue){
        diffFloor *= 10
      }
      var minFloor = 1
      //to prevent the case when the min and the max value are too different (like max = 15000 and min 100)
      while (minObjValue / minFloor > 0) {
        minFloor *= 10
      }
      val df = Math.max(diffFloor / 10, 10)
      (Math.max((minObjValue / df) * df, (minObjValue / minFloor) * minFloor), ((maxObjValue / df) * df) + df)
    }
  }

  /**
    * Return the value of the X axis's border
 *
    * @param diffTimeValue The difference between the minTimeValue and the maxTimeValue
    * @param minTimeValue The minimum objective's time of occurrence that will be drawn
    * @param maxTimeValue The maximum objective's time of occurrence that will be drawn
    * @return The left and right border of the X axis
    */
  def getAbsFloorValues(diffTimeValue:scala.Double, minTimeValue:scala.Double, maxTimeValue:scala.Double): (scala.Double, scala.Double) ={
    var diffFloor = 1
    while(diffFloor <= diffTimeValue)
      diffFloor *= 10
    var minFloor = 1
    while(minTimeValue/minFloor > 0){
      minFloor *= 10
    }
    val df = Math.max(diffFloor / 10, 1)
    (Math.max((minTimeValue / df) * df, (minTimeValue / minFloor) * minFloor), Math.min(((maxTimeValue / df) * df) + df,maxXValueDisplayed))
  }

  /**
    * @return The list of elements that will be considered for the drawing of the curve
    */
  def getUsedLists: (scala.List[Int], scala.List[Long], scala.List[Int], scala.List[Color]) = {
    val usedObjValues: ListBuffer[Int] = new ListBuffer[Int]
    val usedObjTimes: ListBuffer[Long] = new ListBuffer[Long]
    val usedObjBestValues: ListBuffer[Int] = new ListBuffer[Int]
    val usedColorValues: ListBuffer[Color] = new ListBuffer[Color]

    for(i <- yValues.length - maxNumberOfXYValues until yValues.length){
      usedObjValues.append(yValues(i))
      usedObjTimes.append(xValues(i))
      usedObjBestValues.append(bestValues(i))
      usedColorValues.append(xColorValues(i))
    }

    val tempObjTimesList = usedObjTimes.toList
    var removedElems = 0
    for(i <- tempObjTimesList.indices){
      if(!(tempObjTimesList(i) > minXValueDisplayed && tempObjTimesList(i) < maxXValueDisplayed)) {
        usedObjValues.remove(i-removedElems)
        usedObjTimes.remove(i-removedElems)
        usedObjBestValues.remove(i-removedElems)
        usedColorValues.remove(i-removedElems)
        removedElems += 1
      }
    }
    //To fill the blank when there is no registered value between the minObjTime and the maxObjTime

    if(usedObjValues.isEmpty){
      val lastIndex = xValues.lastIndexWhere(_<minXValueDisplayed)
      val index = xValues.indexWhere(_>maxXValueDisplayed)
      usedObjValues.append(yValues(lastIndex))
      usedObjTimes.append(minXValueDisplayed)
      usedObjBestValues.append(bestValues(lastIndex))
      usedColorValues.append(xColorValues(lastIndex))
      usedObjValues.append(yValues(index))
      usedObjTimes.append(maxXValueDisplayed)
      usedObjBestValues.append(bestValues(index))
      usedColorValues.append(xColorValues(index))
    }else {
      //To fill the blank at the end of the currently drawn curve
      usedObjValues.append(usedObjValues.last)
      usedObjTimes.append(maxXValueDisplayed)
      usedObjBestValues.append(usedObjBestValues.last)
      usedColorValues.append(usedColorValues.last)
      //To fill the blank at the beginning of the currently drawn curve
      usedObjValues.insert(0,usedObjValues.head)
      usedObjTimes.insert(0,minXValueDisplayed)
      usedObjBestValues.insert(0,usedObjBestValues.head)
      usedColorValues.insert(0,usedColorValues.head)
    }
    (usedObjValues.toList,usedObjTimes.toList,usedObjBestValues.toList,usedColorValues.toList)
  }

  /**
    * Draw the axis
 *
    * @param minY The bottom border of the Y axis
    * @param maxY The top border of the Y axis
    * @param minX The left border of the X axis
    * @param maxX The right border of the X axis
    */
  def drawAxis(minY:Long, maxY:Long, minX:scala.Double, maxX:scala.Double): Unit ={
    val ordLine = new VisualLine(this,new Double(minWidth,minHeight,minWidth,maxHeight))
    ordLine.outerCol_$eq(Color.black)
    val absLine = new VisualLine(this,new Double(minWidth,maxHeight,maxWidth,maxHeight))
    absLine.outerCol_$eq(Color.black)

    val objStep = (maxY - minY)/10
    for(i <- 1 to 10){
      val scaleHeight = adjustHeight(i*objStep + minY,minY,maxY)
      new VisualText(this,5,scaleHeight,(minY+objStep*i).toString,false,new Rectangle2D.Double(0, 0, 1, 1))
      new VisualLine(this,new Double(minWidth-10,scaleHeight,minWidth,scaleHeight))
    }
    val timeStep = (maxX - minX)/10
    for(i <- 1 to 10){
      val scaleWidth = adjustWidth(i*timeStep + minX, minX,maxX)
      new VisualText(this,scaleWidth,maxHeight+20,((timeStep*i) + minX).toInt.toString,true,new Rectangle2D.Double(0, 0, 1, 1))
      new VisualLine(this,new Double(scaleWidth,maxHeight,scaleWidth,maxHeight+10))
    }
  }
}
