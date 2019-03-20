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
/******************************************************************************
 * Contributors:
 *     This code has been initially developed by CETIC www.cetic.be
 *         by Renaud De Landtsheer
 ******************************************************************************/

package oscar.cbls.core.draft.constraint

import oscar.cbls.core.draft.objective.Objective
import oscar.cbls.algo.quick.QList
import oscar.cbls.core.draft.computation._
import oscar.cbls.core.draft.computation.core.ChangingValue
import oscar.cbls.core.draft.computation.old.ChangingIntValue

case class NamedConstraint(name:String, baseConstraint:Constraint) extends Constraint{
  /** returns the violation associated with variable v in this constraint
    * all variables that are declared as constraint should have an associated violation degree.
    * notice that you cannot create any new invariant or variable in this method
    * because they can only be created before the model is closed.
    * */
  override def violation(v: ChangingValue): ChangingIntValue = baseConstraint.violation(v)

  /** returns the degree of violation of the constraint
    * notice that you cannot create any new invariant or variable in this method
    * because they can only be created before the model is closed.
    * @return
    */
  override def violation: ChangingIntValue = baseConstraint.violation

  override def constrainedVariables:QList[ChangingValue] = baseConstraint.constrainedVariables

  override def checkInternals(){
    baseConstraint.checkInternals()}

  override protected def registerConstrainedVariable(v: ChangingValue): Unit = throw new Error("should do this at the base constraint")

  override def toString: String = name + ":" + baseConstraint

  override def detailedString(short: Boolean, indent: Int): String = nSpace(indent) + name + "{\n" + baseConstraint.detailedString(short,indent+2) + "}"

  override def store: Store = baseConstraint.store
}


/**A constraint is a function that computes a degree of violation that is managed as any invariant.
 * This degree of violation is obtained through the violation method.
 * Furthermore, each variable involved in the constraint also gets an associated violation.
 * This variable-specific violation quantifies the involvement of the variable in the overall violation of the constraint.
 * It can be obtained through the violation(v: Variable) method.
 * All these violation are stored as IntVar, so that they can be involved in the construction of complex formulas,
 * and managed as invariants.
  * @author renaud.delandtsheer@cetic.be
 */
trait Constraint extends Objective{


  //TODO: add a diagnostic string explaining why the constraint is violated??

  def value:Int = violation.value

  //use this to name a constraint. it will return a named constraint that you should post in your constraint system instead of this one
  def nameConstraint(name:String):NamedConstraint = NamedConstraint(name,this)

  /** returns the degree of violation of the constraint
    * notice that you cannot create any new invariant or variable in this method
    * because they can only be created before the model is closed.
    * @return
    */
  def violation: ChangingIntValue

  /**facility to check that the constraint is enforced
    * */
  def isTrue: Boolean = value == 0


  /**the variables that are constrained by the constraint.
    * This should be read only. If you want to declare more constrained variables,
    * use the registerConstrainedVariable method. */
  private var _constrainedVariables:QList[ChangingValue] = null

  def constrainedVariables:QList[ChangingValue] = _constrainedVariables

  /**This should be called by the constraint to declare the set of constrained variables.
    * This should be done at the same time as the registration for invariant API.
    * The sole purpose of this is to know which variables have an associated degree of violation.
    * This is not correlated with the registration for dependencies in the invariants.
    * e.g.: A constraint can constrain a variable,
    * but subcontract the computation and implementation of the constraint to invariants.
    * Notice that all variables sent here which are actually constants are not kept, as they are not variables, actually.
    * This is tested by looking that the variable has a model associated.
    *
    * notice that constants will simply not be registered, so they will never have a violation degree stored anywhere.
    *
    * @param v the variable that is declared as constrained by the constraint
    */
  protected def registerConstrainedVariable(v: ChangingValue){
    _constrainedVariables = QList(v,_constrainedVariables)
  }

  protected def registerConstrainedVariables(v: ChangingValue*){
    for (vv <- v){registerConstrainedVariable(vv)}
  }

  /** returns the violation associated with variable v in this constraint
   * all variables that are declared as constraint should have an associated violation degree.
    * notice that you cannot create any new invariant or variable in this method
    * because they can only be created before the model is closed.
    * */
  def violation(v: ChangingValue): ChangingIntValue

  def checkInternals() {}
}