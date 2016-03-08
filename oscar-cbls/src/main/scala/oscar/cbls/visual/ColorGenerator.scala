package oscar.examples.cbls.routing.visual

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

import java.awt.Color

/**
  * The utility of this object is to generate a pseudo-random array of color.
  * For the same amount of color needed, the returned array will always contain the same colors
  * but in a different order.
  * @author fabian.germeau@student.vinci.be
  */
object ColorGenerator {
  def generateRandomColors(number:Int): Array[Color] ={
    val maxColorNumber = getMaxColorNumber(number)
    val colorValues = new Array[Color](Math.pow(maxColorNumber,3).toInt)
    var i = 0
    for(c1 <- if(Math.random()<0.5)0 until maxColorNumber else maxColorNumber-1 until -1 by -1){
      val r = c1*(255/maxColorNumber)
      for(c2 <- if(Math.random()<0.5)0 until maxColorNumber else maxColorNumber-1 until -1 by -1){
        val g = c2*(255/maxColorNumber)
        for(c3 <- if(Math.random()<0.5)0 until maxColorNumber else maxColorNumber-1 until -1 by -1){
          val b = c3*(255/maxColorNumber)
          println(r,g,b)
          colorValues(i) = new Color(r,g,b)
          i += 1
        }
      }
    }
    colorValues
  }

  /**
    *
    * @param number the number of colors needed
    * @param exp the current base value
    * @return 
    */
  def getMaxColorNumber(number:Int,exp:Int = 1):Int = {
    if(Math.pow(exp,3) < number)
      getMaxColorNumber(number,exp+1)
    else
      exp
  }

  /**
    * Generate a color from an hashcode
    * @param hash the hashcode
    * @return
    */
  def generateColorFromHash(hash:Int): Color = {
    val absHash = Math.abs(hash)
    val r = absHash%255
    val g = (absHash/255)%255
    val b = ((absHash/255)/255)%255
    new Color(r,g,b)
  }
}
