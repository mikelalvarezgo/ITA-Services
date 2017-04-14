package domain.gatherer.exception

import domain.exception.ITAException

case class GathererException(
  code: String,
  message: String,
  cause: Option[Throwable] = None) extends ITAException

object GathererException {

  def errorCode(code: Int): String = s"GATHERER-$code"

  val pickUpControllerCreatePickUpAlreadyCreate = errorCode(1001)
  val pickUpControllerCreatePickUpErrorCreatingDB = errorCode(1002)
  val pickUpControllerCreatePickUpErrorInternalError = errorCode(1003)

  val pickUpControllerUpdatePickUpNotFound = errorCode(1005)
  val pickUpControllerUpdatePickUpInternalError = errorCode(1006)

  val pickUpControllerGetPickUpNotFound = errorCode(1007)

}
