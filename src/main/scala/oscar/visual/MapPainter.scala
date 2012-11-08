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
package oscar.visual;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Set;

import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.mapviewer.DefaultWaypointRenderer;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.mapviewer.Waypoint;
import org.jdesktop.swingx.mapviewer.WaypointRenderer;
import org.jdesktop.swingx.painter.Painter;

/**
 * @author Pierre Schaus
 */
class MapPainter(map : VisualMap) extends Painter[JXMapViewer] {
	var mymap = map
	
	
	private val renderer = new DefaultWaypointRenderer();

	

	def paint(gin : Graphics2D ,  map : JXMapViewer,  w : Int, h : Int) {
		var g =  gin.create().asInstanceOf[Graphics2D]
		
		//convert from viewport to world bitmap
		val rect = mymap.viewer.getViewportBounds()
		g.translate(-rect.x, -rect.y)

		/*
		 * draw : 
		 */
		//lines
		g.setColor(Color.RED)
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		g.setStroke(new BasicStroke(2))
		

		for ( l <- mymap.lines) {

			//convert geo to world bitmap pixel 
			val pt1 = mymap.viewer.getTileFactory().geoToPixel(new GeoPosition(l.lt1, l.lg1), mymap.viewer.getZoom())
			val pt2 = mymap.viewer.getTileFactory().geoToPixel(new GeoPosition(l.lt2, l.lg2), mymap.viewer.getZoom())

			g.drawLine( pt1.getX().toInt, pt1.getY().toInt,  pt2.getX().toInt,  pt2.getY().toInt) 

		}
		
		//paths
		g.setColor(Color.BLACK)
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
		g.setStroke(new BasicStroke(2))
		

		for ( l <- mymap.paths.map(_.lines).flatten) {

			//convert geo to world bitmap pixel 
			val pt1 = mymap.viewer.getTileFactory().geoToPixel(new GeoPosition(l.lt1, l.lg1), mymap.viewer.getZoom())
			val pt2 = mymap.viewer.getTileFactory().geoToPixel(new GeoPosition(l.lt2, l.lg2), mymap.viewer.getZoom())

			g.drawLine( pt1.getX().toInt, pt1.getY().toInt,  pt2.getX().toInt,  pt2.getY().toInt) 

		}
		
		//waypoints
		g.setColor(Color.BLUE)
		for (wp <- mymap.waypoints) {
            val pt1 = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom())
            val x =  pt1.getX().toInt
            val y = pt1.getY().toInt
            
            g.setStroke(new BasicStroke(3f))
            g.setColor(Color.BLUE)
            g.drawOval(x-10,y-10,20,20)
            g.setStroke(new BasicStroke(1f))
            g.drawLine(x-10,y-0,x+10,y+0)
            g.drawLine(x-0,y-10,x+0,y+10)
            
            
            
            
		}
		

		

		g.dispose()
	}
	
	protected def paintWaypoint(w : Waypoint, g :  Graphics2D) {
        renderer.paintWaypoint(g, mymap.viewer, w)
    }
}

