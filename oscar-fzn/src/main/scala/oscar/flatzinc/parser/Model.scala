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
/**
 * @author Jean-Noël Monette
 */
package oscar.flatzinc.parser

import oscar.flatzinc.model.FZProblem
import scala.collection.mutable.{Map, Set => MSet}
import oscar.flatzinc.parser.intermediatemodel._
import oscar.flatzinc.model.Annotation
import scala.collection.JavaConverters._
import java.util.ArrayList
import oscar.flatzinc.model.FZProblem
import oscar.flatzinc.model._
import oscar.flatzinc.NoSuchConstraintException
import java.lang.reflect.Constructor
import oscar.flatzinc.ParsingException
import oscar.flatzinc.cbls.Log
import scala.collection.mutable.WrappedArray


class VarRef(val v: Variable) extends Element()

class Model(val log: Log) {
  
  val problem: FZProblem = new FZProblem()
  val dico: Map[String,Element] = Map.empty[String,Element]
  val dicoAnnot: Map[String, List[Annotation]] = Map.empty[String,List[Annotation]]
  
  def addId(id: String, e: Element)={
    //Console.err.println("% added" + id)
    dico(id) = e
  }
  def findId(id: String): Element = {
    if(dico.contains(id))dico(id)
    else{//TODO: That should be an annotation, how to check this?
      val e = new Element();
      e.name = id;
      e
    }
  }
  
  def createDomain(e: Domain,t: String): Domain  = {
    if(e==null){
      if(t.equals("bool")) new DomainRange(0,1)
      else new DomainRange(Int.MinValue, Int.MaxValue)//TODO: This is dangerous!
    }else e;      
  }
  def copy(d: Domain): Domain = {
    d match {
      case DomainSet(v) => new DomainSet(v)
      case DomainRange(mi,ma) => new DomainRange(mi,ma)
      case _ => null.asInstanceOf[Domain]
    }
  }
  def addNewVariable(t: Type, de: Element, name: String, anns0: java.util.List[Annotation])={
    val anns = anns0.asScala.toList;
    val d = createDomain(if(de!=null)de.value.asInstanceOf[Domain]else null,t.typ)
    if(!(t.typ.equals("int")||t.typ.equals("bool")))
      throw new ParsingException("Only Supporting Int and Bool variables.");
    if(t.isArray) {
        val a = new ArrayOfElement();
        if(d!=null)a.domain = d;
        a.name = name;
        a.typ = t;
        a.annotations = anns0;
        for(i <- 0 to t.size-1){
            val n = name+"["+(i+1)+"]"
            val v = problem.addVariable(n,copy(d),t.typ.equals("bool"));
            val vr = new VarRef(v);
            a.elements.add(vr);
            vr.typ = new Type(t);
            vr.typ.isArray = false;
            vr.typ.size = 1;
            vr.name = n;
            if(d!=null)vr.domain = d;
        }
        addId(name,a);
        handleVarAnnotations(name, a, anns)
      }else{
        val v = problem.addVariable(name,d,t.typ.equals("bool"))
        val vr = new VarRef(v);
        if(d!=null)vr.domain = d;
        vr.name = name;
        vr.typ = t;
        vr.annotations = anns0;
        addId(name,vr);
        handleVarAnnotations(name, vr, anns)
      }
  }
  def addAliasVariable(t: Type, de: Element, name: String, e:Element, anns: java.util.List[Annotation])={
    //TODO: When can de be null?
    val d = if(de!=null)de.value.asInstanceOf[Domain]else null
    //if(!name.equals(e.name)) System.out.println("% Not the same name: "+e.name+" vs "+name);
    if(!t.equals(e.typ)){
      if(e.typ.typ.equals("null")){
        e.typ.typ = t.typ;
      }else{
        log(0,"Not the same type: "+e.typ+" vs "+t);
      }
    }
    if(d!=null && !d.equals(e.domain)){
      //System.out.println("% Not the same domain: "+e.domain+" vs "+d);
      if(e.domain==null)e.domain = d
      else e.domain.inter(d)
    }
    //if(!anns.equals(e.annotations)) System.out.println("% Not the same annotations: "+e.annotations+" vs "+anns);
    addId(name,e);
    handleVarAnnotations(name, e, anns.asScala.toList)
  }
  
  def isIntroducedVar(id: String): Boolean = {
    dicoAnnot(id).exists(_.name == "var_is_introduced");
  }
  def isDefinedVar(id: String): Boolean = {
    dicoAnnot(id).exists(_.name == "is_defined_var");
  }
  def isOutputVar(id: String): Boolean = {
    dicoAnnot(id).exists(_.name == "output_var")
  }
  
  private def handleVarAnnotations(name: String, e: Element, anns: List[oscar.flatzinc.model.Annotation]): Any = {
    dicoAnnot(name) = anns;
    if(e.typ.isArray){
      if (anns.exists((a: Annotation) => a.name == "output_array")) {
        
        val a = e.asInstanceOf[ArrayOfElement]
          if(e.typ.typ.equals("int")){
            problem.solution.addOutputArrayVarInt(name,a.elements.asScala.toArray.map(_.asInstanceOf[VarRef].v.id),
                           anns.find((p:Annotation) => p.name == "output_array").get.args(0).asInstanceOf[ArrayOfElement].elements.asScala.toList.map(e=>e.value.asInstanceOf[DomainRange].toRange))
          }
          if(e.typ.typ.equals("bool")){
            problem.solution.addOutputArrayVarBool(name,a.elements.asScala.toArray.map(_.asInstanceOf[VarRef].v.id),
                           anns.find((p:Annotation) => p.name == "output_array").get.args(0).asInstanceOf[ArrayOfElement].elements.asScala.toList.map(e=>e.value.asInstanceOf[DomainRange].toRange))
          }
        }
    }else{
      if(anns.exists((a: Annotation) => a.name == "output_var")) {
        //println("000000 "+name)
        if(e.typ.typ.equals("int")) problem.solution.addOutputVarInt(name,e.name)
        if(e.typ.typ.equals("bool"))problem.solution.addOutputVarBool(name,e.name)
      }
    }
  }
  
  
  
  def addConstraint(name: String, args: java.util.List[Element], anns: java.util.List[Annotation]) = {
    val (ann_def,ann_other) = anns.asScala.toList.partition(a => a.name == "defines_var")
    val cstr = constructConstraint(name, args.asScala.toList, anns.asScala.toList)
    //Added the test because Mzn 2.0 adds some defined_var(12) with constants.
    ann_def.foreach(a => if(a.args(0).isInstanceOf[VarRef])cstr.setDefinedVar(a.args(0).asInstanceOf[VarRef].v))
    problem.addConstraint(cstr)
  }
  
  def setSATObjective(anns: java.util.List[Annotation])= {
    problem.satisfy(anns.asScala.toList)
    //TODO: Search annotations are ignored for now
    if(anns.size() > 0)log(0,"ignoring search annotations")
  }
  def setMINObjective(e: Element, anns: java.util.List[Annotation])= {
    problem.minimize(getIntVar(e),anns.asScala.toList)
    //TODO: Search annotations are ignored for now
    if(anns.size() > 0)log(0,"ignoring search annotations")
  }
  def setMAXObjective(e: Element, anns: java.util.List[Annotation])= {
    problem.maximize(getIntVar(e),anns.asScala.toList)
    //TODO: Search annotations are ignored for now
    if(anns.size() > 0)log(0,"ignoring search annotations")
  }
  def getIntVar(e: Element): IntegerVariable = {
    if(e.isInstanceOf[VarRef])e.asInstanceOf[VarRef].v.asInstanceOf[IntegerVariable];
    else if(e.value.isInstanceOf[Integer])new IntegerVariable(e.value.toString(),Int.unbox(e.value))
    //TODO: This method should only be used when IntegerVariable are used 
    //else if(e.value.isInstanceOf[Boolean])new BooleanVariable(e.value.toString(),Some(Boolean.unbox(e.value)))
    else{
      throw new ParsingException("Expected a var int but got: "+e)
      //null.asInstanceOf[Variable]
    }
  }
  def getBoolVar(e: Element): BooleanVariable = { 
    if(e.isInstanceOf[VarRef])e.asInstanceOf[VarRef].v.asInstanceOf[BooleanVariable];
    else if(e.value.isInstanceOf[Boolean])new BooleanVariable(e.value.toString(),Some(Boolean.unbox(e.value)))
    else{
      throw new ParsingException("Expected a var bool but got: "+e)
      //null.asInstanceOf[Variable]
    }
  }
  def getBoolVarArray(e: Element): Array[BooleanVariable] = { 
    //TODO: Do the same memoization as for Integer arrays.
    if(e.isInstanceOf[ArrayOfElement]){
      val a = e.asInstanceOf[ArrayOfElement]
      a.elements.asScala.toArray.map(v => getBoolVar(v))
    }else{
      throw new ParsingException("Expected a array of var bool but got: "+e)
    }
  }

  //TODO: Check if this actually reduces the memory footprint and does not increase parsing time too much...
  var knownarrays = Map.empty[WrappedArray[IntegerVariable],Array[IntegerVariable]]
  
  def getIntVarArray(e: Element): Array[IntegerVariable] = { 
    if(e.isInstanceOf[ArrayOfElement]){
      val array = e.asInstanceOf[ArrayOfElement].elements.asScala.toArray.map(v => getIntVar(v))
      val wrap = genericWrapArray(array)
      if(knownarrays.contains(wrap)){
       // Console.err.println("% reuse "+knownarrays.size)
        knownarrays(wrap)
      }else{
        knownarrays(wrap) = array
        array
      }
    }else{
      throw new ParsingException("Expected a array of var int but got: "+e)
    }
  }
  def getIntSet(e: Element): Domain = {
    //println("%"+e)
    e.value.asInstanceOf[Domain]
  }
  def constructConstraint(cstr: String, varList: List[Element], ann:List[Annotation]): Constraint = {
    //special case
    if(cstr=="bool_eq_reif" && !varList(1).typ.isVar && !varList(1).value.asInstanceOf[Boolean]){
      return constructConstraint("bool_not",varList.head :: varList.tail.tail,ann)
    }
    if(cstr.endsWith("_reif"))reif(constructConstraint(cstr.substring(0,cstr.length-5),varList.dropRight(1),ann),getBoolVar(varList.last))
    else
      cstr match {
        case "oscar_alldiff" =>
          log(0,"deprecated: oscar_alldiff")
          val a = getIntVarArray(varList(0));
          all_different_int(a, ann)
        case other =>
          makeConstraint(other,varList,ann)
          //throw new NoSuchConstraintException(notImplemented.toString(),"CBLS Solver");

      }
  }
  
  def makeConstraint(c: String, args:List[Element], ann:List[Annotation]): Constraint = {
    try{
      val cl = Class.forName("oscar.flatzinc.model."+c)
      makeConstraint(cl,args,ann)
    }catch{
      case e: ClassNotFoundException => throw new NoSuchConstraintException(c,"Intermediate Representation");
    }
  }
  
  def makeConstraint[A]/* <: Constraint]*/(c:Class[A],args:List[Element], ann:List[Annotation]): Constraint = {
    val cc:Constructor[A] = c.getConstructors()(0).asInstanceOf[Constructor[A]];
    val p = cc.getParameterTypes();
    //println(p.mkString(","))
    val arg = new Array[Object](p.length)
    for(i <- 0 to p.length-2){
      /*arg(i) = types(i) match {
                    case "av" => getIntVarArray(args(i))
                    case "v" => getIntVar(args(i))
      }*/
      arg(i) = if (p(i).equals(classOf[Array[IntegerVariable]])) getIntVarArray(args(i))
                else if (p(i).equals(classOf[IntegerVariable])) getIntVar(args(i))//TODO: differentiate par vs var
                else if (p(i).equals(classOf[Array[BooleanVariable]])) getBoolVarArray(args(i))
                else if (p(i).equals(classOf[BooleanVariable])) getBoolVar(args(i))//TODO: differentiate par vs var
                else if(p(i).equals(classOf[Domain])) getIntSet(args(i))
                else throw new Exception("Case not handled: "+p(i));
    }
    arg(p.length-1) = ann;
    //println(arg.length)
    val x = arg;//new AsJava(arg)
    val h = new Help()
    h.buildConstraint(cc.asInstanceOf[Constructor[Constraint]],x/*.asJava*/)
    //cc.newInstance(x).asInstanceOf[Constraint]
    //.tupled(arg)
  }
  
  def createDomainSet(s:java.util.Set[Integer]): DomainSet = {
    new DomainSet(s.asScala.map(i=>Int.unbox(i)).toSet)
  }
}


  