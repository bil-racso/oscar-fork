package oscar.examples.cbls.car

import oscar.cbls.invariants.core.computation.CBLSIntVar
import oscar.cbls.modeling.CBLSModel
import oscar.cbls.objective.Objective
import oscar.cbls.search.core.Neighborhood
import oscar.cbls.search.{Benchmark, RollNeighborhood}
import oscar.cbls.search.combinators.{DynAndThen, Profile}
import oscar.cbls.search.move.SwapMove

import scala.collection.immutable.SortedMap
import scala.language.postfixOps
import scala.util.Random

/**
 * Created by rdl on 29-01-16.
 */
object carSequencerBenchmarker  extends CBLSModel with App {

  val orderedCarsByType:SortedMap[Int,Int] = SortedMap(0 -> 90, 1 -> 60, 2 -> 110 , 3 -> 120, 4 -> 40, 5 -> 30)
  val carTypes = 0 to 5

  println("carSequencing")
  println("orderedCarTypes:" + orderedCarsByType)

  //option types
  //   A   G   D   E
  //0  T   T   T   F
  //1  F   T   T   F
  //2  T   F   T   F
  //3  F   F   F   T
  //4  T   T   F   T
  //5  F   T   F   T

  def makeBoolArray(values:Int*):Array[Boolean] = {
    val toReturn = Array.fill(carTypes.end +1)(false)
    values.foreach(toReturn(_) = true)
    toReturn
  }
  val airCoCarTypes = makeBoolArray(0,2,4)
  val automaticGearBoxCarTypes = makeBoolArray(0,1,4,5)
  val dieselCarTypes = makeBoolArray(0,1,2)
  val espCarTypes = makeBoolArray(3,4,5)

  val maxType = orderedCarsByType.keys.max
  val minType = orderedCarsByType.keys.min
  val typeRange = minType to maxType
  def prependItems(acc:List[Int],n:Int,item:Int):List[Int] = (if(n == 0) acc else prependItems(item :: acc,n-1,item))
  val orderedCarTypes:List[Int] = orderedCarsByType.foldLeft(List.empty[Int])({case (accList,(carType,nbItems)) => prependItems(accList,nbItems,carType)})
  val nbCars = orderedCarTypes.size

  println("totalNumberOfCars:" + nbCars)

  //initializes the car sequence in a random way
  val orderedCarTypesIterator = Random.shuffle(orderedCarTypes).toIterator
  val carSequence:Array[CBLSIntVar] = Array.tabulate(nbCars)(p => CBLSIntVar(orderedCarTypesIterator.next(),typeRange,"carClassAtPosition" + p)).toArray

  //airConditionner: max 2 out of 3
  c.post(sequence(carSequence,3,2,airCoCarTypes))

  //automaticGearBox: max 3 out of 5
  c.post(sequence(carSequence,5,3,automaticGearBoxCarTypes))

  //diesel: max 3 out of 5
  c.post(sequence(carSequence,5,3,dieselCarTypes))

  //esp: max 2 ouf of 3
  c.post(sequence(carSequence,3,2,espCarTypes))

  val impactZone = 5

  val varViolation = c.violations(carSequence)
  val violatedCars = filter(varViolation)
  val mostViolatedCars = argMax(varViolation)

  println("closing model")

  c.close
  val obj:Objective = c.violation

  s.close()

  val swap = swapsNeighborhood(carSequence,"swapCars")
  val rollViolated = RollNeighborhood(carSequence, name = "RollViolatedCars", maxShiftSize = _ => 20, searchZone = violatedCars)
  val roll = RollNeighborhood(carSequence, name = "RollAllCars", maxShiftSize = _ => 10)
  val mostViolatedSwap = swapsNeighborhood(carSequence,"mostViolatedSwap", searchZone2 = mostViolatedCars, symmetryCanBeBrokenOnIndices = false)
  val shiftNeighbor = shiftNeighborhood(carSequence, searchZone1 =() => violatedCars.value.toList, maxShiftSize = carSequence.length/2/*, maxOffsetSize = carSequence.length/2*/, hotRestart = true)
  val rollNeighbor = rollNeighborhood(carSequence)

  val linkedDoubleSwaps = DynAndThen(
    swapsNeighborhood(carSequence,"swapCars1"),
    ((swapMove:SwapMove) => {
      val indices = List(swapMove.idI, swapMove.idJ)
      swapsNeighborhood(carSequence, "swapCars2", searchZone1 = () => indices, symmetryCanBeBrokenOnIndices = false, symmetryCanBeBrokenOnValue = true)
    })) name "linkedDoubleSwaps"

  val doubleSwaps = (swapsNeighborhood(carSequence,"swapCars1") andThen swapsNeighborhood(carSequence,"swapCars2")) name "doubleSwaps"

  val looselyLinkedDoubleSwaps = DynAndThen(
    swapsNeighborhood(carSequence,"swapCars1", symmetryCanBeBrokenOnIndices = false),
    ((swapMove:SwapMove) => {
      val firstSwappedCar = 0.max(swapMove.idI - impactZone) until (nbCars).min(swapMove.idI + impactZone)
      val otherSwappedCar = (nbCars-1).min(swapMove.idJ+1) until nbCars
      swapsNeighborhood(carSequence, "swapCars2", searchZone1 = () => firstSwappedCar, searchZone2 = () => otherSwappedCar, symmetryCanBeBrokenOnIndices = false)
    })) name "looselyLinkedDoubleSwaps"


  val search1 = (
    (mostViolatedSwap random swap)
      orElse (shiftNeighbor)
      orElse (shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")  maxMoves (10))
      orElse (shuffleNeighborhood(carSequence, violatedCars, name = "shuffleAllViolatedCars") maxMoves (10))
      orElse (shuffleNeighborhood(carSequence, name = "globalShuffle") maxMoves 5)
      maxMoves nbCars *2 withoutImprovementOver obj
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  val search2 = (
    mostViolatedSwap orElse roll
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")) guard(() => mostViolatedCars.value.size > 2), 5, obj)
      orElse shiftNeighbor
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, violatedCars, name = "shuffleSomeViolatedCars", numberOfShuffledPositions = () => violatedCars.value.size/2)), 2, obj)
      orElse (shuffleNeighborhood(carSequence, name = "shuffleAllCars") maxMoves 5)
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  val search3 = (
    ((mostViolatedSwap random swap) orElse rollViolated
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")) guard(() => mostViolatedCars.value.size > 2), 5, obj)
      exhaustBack shiftNeighbor)
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, violatedCars, name = "shuffleSomeViolatedCars", numberOfShuffledPositions = () => violatedCars.value.size/2)), 2, obj)
      orElse (shuffleNeighborhood(carSequence, name = "shuffleAllCars") maxMoves 5)
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  val search4 = (
    ((mostViolatedSwap random swap) orElse rollViolated
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")) guard(() => mostViolatedCars.value.size > 2), 5, obj)
      exhaustBack shiftNeighbor)
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, violatedCars, name = "shuffleSomeViolatedCars", numberOfShuffledPositions = () => violatedCars.value.size/2)), 2, obj)
      orElse (shuffleNeighborhood(carSequence, name = "shuffleAllCars") maxMoves 5)
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  val search5 = (
    mostViolatedSwap random roll
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")) guard(() => mostViolatedCars.value.size > 2), 5, obj)
      orElse shiftNeighbor
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, violatedCars, name = "shuffleSomeViolatedCars", numberOfShuffledPositions = () => violatedCars.value.size/2)), 2, obj)
      orElse (shuffleNeighborhood(carSequence, name = "shuffleAllCars") maxMoves 5)
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  val search6 = (
    mostViolatedSwap random roll
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, mostViolatedCars, name = "shuffleMostViolatedCars")) guard(() => mostViolatedCars.value.size > 2), 4, obj)
      orElse shiftNeighbor
      onExhaustRestartAfter(Profile(shuffleNeighborhood(carSequence, violatedCars, name = "shuffleSomeViolatedCars", numberOfShuffledPositions = () => violatedCars.value.size/2)), 1, obj)
      orElse (shuffleNeighborhood(carSequence, name = "shuffleAllCars") maxMoves 6)
      guard (() => c.violation.value > 0)
      saveBestAndRestoreOnExhaust obj)

  println(Benchmark.benchToStringSimple(obj, 50, List(search1,search2,search3, search4,search5,search6),verbose = 0))

}