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
/*******************************************************************************
  * Contributors:
  *     This code has been initially developed by Ghilain Florent.
  ******************************************************************************/

package oscar.cbls.routing.visual
import java.awt._
import javax.swing._
import oscar.cbls.routing._
import neighborhood._
import oscar.visual._
import oscar.visual.shapes.VisualLine
import oscar.visual.shapes.VisualCircle
import oscar.visual.shapes.VisualArrow
import oscar.visual.plot.PlotLine

/*
object PanelVRP {
  val easyMode = true // to modify to change the user's GUI in a easier panel.

  // management of all GUI component.
  val boardPanel = PanelVRP.boardPanel // board panel
  val mapPanel = PanelVRP.mapPanel // map panel

  val vrpModel = PanelVRP.vrpModel // vrp model



}
*/


class PanelVRP(easyMode:Boolean) extends JPanel{

  val isEasyMode = easyMode

  var myLayout:GridBagLayout = new GridBagLayout
  var myConstraints:GridBagConstraints=null

  val mapPanel:VisualDrawing = newMapPanel
  val plotPanel:PlotLine = newPlotPanel()
  val boardPanel:Dashboard = newBoardPanel()
  val vrpModel = new ModelVRP()
  val colorsManagement = new ColorManagement()

  setGridBagLayout()
  setBackground(Color.white)

  val vrpSearch = new SearchVRP(this) // vrp search strategy
  val vrpSmartSearch =  new SmartSearch(this)

  /*
    Returns the neighborhood selected in the board panel.
   */
  def getSelectedNeighborhood(closeNeighbors:Int, previousMove:Neighbor):Neighbor = {
    val vrp = vrpModel.vrp
    val kLimit = vrpModel.closeNeighbor

    boardPanel.neighborhood.getSelectedIndex match{
      case 0 => OnePointMove.getFirstImprovingMove(vrp, vrp.getKNearest(kLimit),previousMove)
      case 1 => ReinsertPoint.getBestMove(vrp)
      case 2 => RemovePoint.getBestMove(vrp)
      case 3 => Swap.getFirstImprovingMove(vrp,vrp.getKNearest(kLimit),previousMove)
      case 4 => ThreeOptA.getFirstImprovingMove(vrp, vrp.getKNearest(kLimit), previousMove)
      case 5 => ThreeOptB.getFirstImprovingMove(vrp, vrp.getKNearest(kLimit), previousMove)
      case 6 => ThreeOptC.getFirstImprovingMove(vrp, vrp.getKNearest(kLimit), previousMove)
      case 7 => TwoOpt.getFirstImprovingMove(vrp,vrp.getKNearest(kLimit),previousMove)
      case 8 => {
        if(CompositeMove.declaration == null){
          val declaration:CompositeDeclaration = CompositeDeclaration(vrp)
          CompositeMove.attachDeclaration(declaration)
        }
        CompositeMove.getMove(vrp,previousMove)
      }
      case _ => null
    }
  }

  /**
   * Displays nodes on map panel.
   * It assumes a new instances has been build before, else no changes to update.
   */
  def displayNodes(){
    val nodes = vrpModel.towns
    colorsManagement.setDifferentColors(vrpModel.V)
    for(i <- 0 until nodes.length){
      val t = nodes(i)
      if (i<vrpModel.V) VisualCircle(mapPanel, t.long,t.lat,10).innerCol = colorsManagement(i+1)
      else VisualCircle(mapPanel, t.long,t.lat,6).innerCol = Color.white
    }
  }

  /*
  * Displays arrows between nodes on map panel.
  * It assumes a new instances has been build before, else no changes to update.
  */
  def displayArrows() {
    val vrp = vrpModel.vrp
    val nodes = vrpModel.towns

    vrpModel.arrows = Array.tabulate(vrpModel.N)(i => {
      val arrow =
        if (vrp.isRouted(i)) VisualArrow(mapPanel,nodes(i).long,nodes(i).lat,
          nodes(vrp.Next(i).value).long,nodes(vrp.Next(i).value).lat,4)
        else VisualArrow(mapPanel,nodes(i).long,nodes(i).lat,nodes(i).long,nodes(i).lat,4)
      if(vrp.isRouted(i)) setColorToRoute(arrow,vrp.RouteNr(i).value)
      arrow
    })
  }

  def setColorToRoute(l:VisualLine ,i:Int){
    l.outerCol = colorsManagement(i+1)
  }

  /*
  * Update the visualisation while strategy's search.
  */
  def updateVisualisation(iteration:Int, force:Boolean =false) {
    val vrp = vrpModel.vrp
    val nodes = vrpModel.towns
    val arrows = vrpModel.arrows

    def update(i: Int) {
      if(vrp.isRouted(i)){
        arrows(i).visible = true
        setColorToRoute( arrows(i),vrp.RouteNr(i).value)
        arrows(i).dest = (nodes(vrp.Next(i).value).long,
          nodes(vrp.Next(i).value).lat)
      }
      else
        arrows(i).visible = false
    }
    plotPanel.addPoint(iteration,vrp.ObjectiveVar.value - vrp.AddedObjectiveFunctions.value)
    if(force || iteration%10 == 0){
      for (i <- 0 until vrp.N) update(i)
      if(boardPanel.writeRoute())
      boardPanel.updateRouteLabel(vrpModel.getRoute(vrp))
    }
  }

  def newMapPanel:VisualDrawing = {
    val mapPanel : VisualDrawing = VisualDrawing(false)
    mapPanel.setPreferredSize(new Dimension(700,700))
    mapPanel.setMinimumSize(new Dimension(500,500))
    mapPanel.setBorder(BorderFactory.createTitledBorder("Map"))
    mapPanel.setBackground(Color.white)
    mapPanel
  }

  def newPlotPanel():PlotLine = {
    val plotPanel = new PlotLine("","Iteration nbr","Distance")
    if(!easyMode){
      plotPanel.setPreferredSize(new Dimension(500,300))
      plotPanel.setMinimumSize(new Dimension(420,200))
    }
    else{
      plotPanel.setPreferredSize(new Dimension(300,300))
      plotPanel.setMinimumSize(new Dimension(250,200))
    }
    plotPanel.setBorder(BorderFactory.createTitledBorder("Plot"))
    plotPanel.setBackground(Color.white)
    plotPanel
  }

  def cleanPlotPanel(){
    plotPanel.getPoints().clear()
  }

  def cleanMapPanel(){
    mapPanel.clear()
  }

  def newBoardPanel():Dashboard = {
    val boardPanel = new Dashboard(easyMode,this)
    boardPanel.setBorder(BorderFactory.createTitledBorder("Board option"))
    boardPanel.setBackground(Color.white)
    if(!easyMode){
      boardPanel.setPreferredSize(new Dimension(450, 500))
      boardPanel.setMinimumSize(new Dimension(400,350))
    }
    else{
      boardPanel.setPreferredSize(new Dimension(250, 230))
      boardPanel.setMinimumSize(new Dimension(250,230))
    }

    boardPanel
  }

  def setGridBagLayout() {
    setLayout(myLayout)
    // constraints of board panel
    myConstraints = new GridBagConstraints
    myConstraints.gridx = 0
    myConstraints.gridy = 0
    myConstraints.gridheight = GridBagConstraints.RELATIVE
    myConstraints.anchor = GridBagConstraints.PAGE_START
    myConstraints.fill = GridBagConstraints.BOTH
    val scrollBoard:JScrollPane = new JScrollPane(boardPanel,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
      if(easyMode) ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER else ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED)
    scrollBoard.setViewportView(boardPanel)


    if(!easyMode){
      scrollBoard.setPreferredSize(new Dimension(400,500))
      scrollBoard.setMinimumSize(new Dimension(350,400))
    }
    else
    { scrollBoard.setMinimumSize(new Dimension(280,250))
      scrollBoard.setPreferredSize(new Dimension(250,250))
    }
    scrollBoard.getVerticalScrollBar().setBackground(Color.white)
    myLayout.setConstraints(scrollBoard,myConstraints)
    //add the board panel scrolled.

    add(scrollBoard)

    //constraints of plot panel
    myConstraints.gridy = 2
    myLayout.setConstraints(plotPanel,myConstraints)
    add(plotPanel)

    //constraints of map panel
    myConstraints.gridx = 1
    myConstraints.gridy =0
    myConstraints.weightx = 1
    myConstraints.weighty = 1
    myConstraints.gridheight = GridBagConstraints.REMAINDER
    myLayout.setConstraints(mapPanel,myConstraints)
    add(mapPanel)
  }

  //actions of board panel
  def makeInstance(b:Boolean) {
    val a = this
    println("make instance pressed")
    class InstanceInThread(b:Boolean) extends Thread{
      override  def run(){
        vrpModel.initModel(a,b)
        cleanMapPanel()
        cleanPlotPanel()
        displayNodes()
        displayArrows()
      }
    }
    new InstanceInThread(b).start()
  }

  def startSearching() {
    new Thread(vrpSearch).start()
  }
  def startSmartSearching() {
    new Thread(vrpSmartSearch).start()
  }
}

