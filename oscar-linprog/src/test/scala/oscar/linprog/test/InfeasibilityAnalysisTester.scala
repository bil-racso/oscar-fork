package oscar.linprog.test

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSuite, Matchers, Tag}
import oscar.algebra._


@RunWith(classOf[JUnitRunner])
class InfeasibilityAnalysisTests extends LinearMathSolverTester{
  override def testSuite(interface: Option[SolverInterface[Linear,Linear,Double]], solverName: String): FunSuite = {
    new InfeasibilityAnalysisTester(interface)(solverName)
  }
}

class InfeasibilityAnalysisTester(interface: Option[SolverInterface[Linear, Linear, Double]])(solverName: String) extends FunSuite with Matchers {

  override def suiteName: String = solverName + " - InfeasibilityAnalysisTester"

  implicit def i = interface.getOrElse{cancel()}

  override def test(testName: String, testTags: Tag*)(testFun: => Unit): Unit = {
    super.test(solverName + " " + testName, testTags: _*)(testFun)
  }

  test("Infeasibility analysis on LP: infeasibilities due to constraints") {
    implicit val model = new Model[Linear, Linear, Double]

    val x1 = VarNumerical("x1", 0, 40)
    val x2 = VarNumerical("x2", -Double.MaxValue, Double.MaxValue)
    val x3 = VarNumerical("x3", -Double.MaxValue, Double.MaxValue)

    maximize(x1 + x2 * 2.0 + x3 * 3.0)

    subjectTo(
      "E1" ||: x2 + x3 <= 20.0, // 0
      "E2" ||: x1 - x2 * 3.0 + x3 <= 30.0, // 1
      "E3" ||: x1 <= 20.0, // 2
      "E4" ||: x1 >= 40.0 // 3
    )

    model.solve match {
      case AInfeasible() =>

      //
      //        val infeasibilitySetTry = solver.analyseInfeasibility()
      //
      //        infeasibilitySetTry.isSuccess should be(true)
      //
      //        Seq(0, 1).foreach { i =>
      //          cstrs(i).infeasible should be(Some(false))
      //        }
      //
      //        Seq(2, 3).foreach { i =>
      //          cstrs(i).infeasible should be(Some(true))
      //        }
      //
      //        Seq(x1, x2, x3).foreach { v =>
      //          v.lowerBoundInfeasible should be(Some(false))
      //          v.upperBoundInfeasible should be(Some(false))
      //        }
      case _ => assert(false)
    }
    //solver.release()
  }

  test("Infeasibility analysis on LP: infeasibilities due to var bounds") {
    implicit val model = new Model[Linear, Linear, Double]

    val x = VarNumerical("x", 20, Double.MaxValue)
    val y = VarNumerical("y", 0, 5)

    minimize(y)

    subjectTo(
      "E" ||: x - y <= 10.0 // 0
    )

    model.solve match {

      case AInfeasible() =>
      //
      //    val infeasibilitySetTry = solver.analyseInfeasibility()
      //
      //    infeasibilitySetTry.isSuccess should be (true)
      //
      //    cstrs.head.infeasible should be (Some(true))
      //
      //    x.lowerBoundInfeasible should be (Some(true))
      //    x.upperBoundInfeasible should be (Some(false))
      //
      //    y.lowerBoundInfeasible should be (Some(false))
      //    y.upperBoundInfeasible should be (Some(true))
    }
    //solver.release()
  }
}