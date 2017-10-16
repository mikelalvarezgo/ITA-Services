package com.ita.domain.gatherer

import com.ita.domain.Model._
import spray.json.{JsonFormat, _}
import spray.json.DefaultJsonProtocol._

object Model {

  implicit val pickUpStateJF : RootJsonFormat[PickUpState] =
    new  RootJsonFormat[PickUpState]{
    override def write(obj: PickUpState): JsValue = JsString(obj.toString)
    override def read(json: JsValue): PickUpState = {
      val JsString(value) = json
      value match {
        case v if v == Created.toString => Created
        case v if v == Ready.toString => Ready
        case v if v == InProcess.toString => InProcess
        case v if v == Stopped.toString => Stopped
        case v if v == Finished.toString => Finished
      }
    }
  }

 implicit val pickUpJF : RootJsonFormat[TweetPickUp] = jsonFormat7(TweetPickUp.apply)

}
