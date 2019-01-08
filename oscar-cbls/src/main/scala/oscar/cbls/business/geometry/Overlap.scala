package oscar.cbls.business.geometry

import org.locationtech.jts.geom.{Geometry, GeometryCollection, Polygon}

object Overlap {

  def holesBetween(s:List[Geometry]):List[Geometry] = {
    var acc = s.head
    var t = s.tail
    while(t.nonEmpty){
      acc = acc union t.head
      t = t.tail
    }
    extractHoles(acc)
  }

  def centroidsOfFreeSpacesIn(s:Iterable[Geometry], outer:Geometry):Iterable[(Int,Int)] = {
    val frees = freeSpacesIn(s:Iterable[Geometry], outer:Geometry):Iterable[Geometry]
    val centroids = frees.map(_.getCentroid)
    centroids.map(centroid => (centroid.getX.toInt,centroid.getY.toInt))
  }

  def freeSpacesIn(s:Iterable[Geometry], outer:Geometry):Iterable[Geometry] = {
    var acc = outer
    var t = s

    while(t.nonEmpty){
      acc = acc difference t.head
      t = t.tail
    }

    extractShapes(acc)
  }

  private def extractHoles(g:Geometry):List[Geometry] = {
    g match {
      case poly: Polygon =>
        val holesArray = Array.tabulate(poly.getNumInteriorRing)(i => poly.getInteriorRingN(i))
        holesArray.toList
      case m: GeometryCollection =>
        val components = Array.tabulate(m.getNumGeometries)(i => m.getGeometryN(i)).toList
        components.flatMap(extractHoles)
    }
  }
  private def extractShapes(g:Geometry):List[Geometry] = {
    g match {
      case poly: Polygon =>
        List(poly)
      case m: GeometryCollection =>
        val components = Array.tabulate(m.getNumGeometries)(i => m.getGeometryN(i)).toList
        components.flatMap(extractShapes)
    }
  }
}