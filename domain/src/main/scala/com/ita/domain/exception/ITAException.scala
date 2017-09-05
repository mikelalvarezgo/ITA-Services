package com.ita.domain.exception

/**
  * Created by mikelalvarezgo on 14/4/17.
  */
trait ITAException extends Exception{
  val code: String
  val message: String
  val cause: Option[Throwable]

  override def getMessage: String = s"$code: $message"
  override def getCause: Throwable = cause.orNull
}
object ITAException{
  def apply(
    _code: String,
    _message: String,
    _cause: Option[Throwable] = None): ITAException =
    new ITAException {
      val cause: Option[Throwable] = _cause
      val message: String = _code
      val code: String = _message
    }

}
