package com.ita.domain.classifier.exception

import com.ita.domain.exception.ITAException

case class ClassifierException(
  code: String,
  message: String,
  cause: Option[Throwable] = None) extends ITAException

object ClassifierException {

  def errorCode(code: Int): String = s"CLASSIFIER-$code"

  val classifierControllerCreateModelAlreadyCreate = errorCode(1001)
  val classifierControllerCreateModelErrorCreatingDB = errorCode(1002)
  val classifierControllerCreateModelErrorInternalError = errorCode(1003)

  val classifierControllerUpdateModelNotFound = errorCode(1005)
  val classifierControllerUpdateModelInternalError = errorCode(1006)
  val classifierControllerGetModelNotFound = errorCode(1007)

  val classifierControllerCreateExecutionAlreadyCreate = errorCode(1011)
  val classifierControllerCreateExecutionErrorCreatingDB = errorCode(1012)
  val classifierControllerCreateExecutionErrorInternalError = errorCode(1013)

  val classifierControllerUpdateExecutionNotFound = errorCode(1015)
  val classifierControllerUpdateExecutionInternalError = errorCode(1016)

  val classifierControllerGetExecutionNotFound = errorCode(1017)

  val executionModelIdNotFound = errorCode(1020)
  val classifierControllerTrainModelExecutionDoesNotExist = errorCode(1021)
  val classifierControllerTrainModelExecutionlErrorInternalError = errorCode(1022)

  val classifierControllerTrainModelNotSupervisedModel = errorCode(1023)

}
