package oscar.cp.mem.visu

import oscar.visual._
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Graphics

class VisualSet(val nadir: (Int, Int)) extends VisualFrame("Relaxation Viewer") {

  val dim = (600, 600)
  
  val xDiffMin = dim._1.toDouble / nadir._1
  val yDiffMin = dim._2.toDouble / nadir._2

  val solCol = Color.RED
  val lineCol = new Color(100, 100, 100)

  setPreferredSize(new Dimension(dim._1, dim._2))
  val drawing = new VisualDrawing(false, true)

  var ratio = 1
  var xBLCorner = 0
  var yBLCorner = 0
  
  // Frame
  add(drawing)
  pack

  def update(points: Array[(Int, Int)]) {  
    
    drawing.shapes = Array()
    
    val xCoeff = ratio*xDiffMin
    val yCoeff = ratio*yDiffMin
    
    for ((xSol, ySol) <- points) {
      
      val x = (xSol-xBLCorner)*xCoeff
      val y = (ySol-yBLCorner)*yCoeff
      
      if (x >= 0 && x < dim._1 && y >= 0 && y < dim._2) {
        
        drawing.addShape(new VisualCircle(drawing, x, y, 1, solCol))
      }
    }
    drawing.repaint()
  }
  
  def best(point: (Int, Int)) {  
    
    val xCoeff = ratio*xDiffMin
    val yCoeff = ratio*yDiffMin
    
    val (xSol, ySol) = point
      
      val x = (xSol-xBLCorner)*xCoeff
      val y = (ySol-yBLCorner)*yCoeff
      
      if (x >= 0 && x < dim._1 && y >= 0 && y < dim._2) {
        
        drawing.addShape(new VisualCircle(drawing, x, y, 2, Color.RED))
      }
 
    drawing.repaint()
  }
  
  def selected(point: (Int, Int)) {  
    
    val xCoeff = ratio*xDiffMin
    val yCoeff = ratio*yDiffMin
    
    val (xSol, ySol) = point
      
      val x = (xSol-xBLCorner)*xCoeff
      val y = (ySol-yBLCorner)*yCoeff
      
      if (x >= 0 && x < dim._1 && y >= 0 && y < dim._2) {
        
        drawing.addShape(new VisualCircle(drawing, x, y, 3, Color.GREEN))
      }
 
    drawing.repaint()
  }
}