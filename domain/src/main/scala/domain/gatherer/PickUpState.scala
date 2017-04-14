package domain.gatherer

/**
  * Created by mikelalvarezgo on 13/4/17.
  */
abstract class PickUpState {
  override def toString = getClass.getName.split("$").last.toUpperCase
}

case object Ready extends PickUpState

case object InProcess extends PickUpState

case object Stopped extends PickUpState

case object Finished extends PickUpState



