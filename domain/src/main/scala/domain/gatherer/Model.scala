package domain.gatherer

import domain.Id
import domain.Model._
import spray.json.{JsonFormat, _}
import spray.json.DefaultJsonProtocol._

import scala.util.parsing.json.JSONFormat

class Model {

  implicit val pickUpStateJF : JsonFormat[PickUpState] = new  JsonFormat[PickUpState]{
    override def write(obj: PickUpState): JsValue = JsString(obj.toString)
    override def read(json: JsValue): PickUpState = {
      val JsString(value) = json
      value match {
        case v if v == Ready.toString => Ready
        case v if v == InProcess.toString => InProcess
        case v if v == Stopped.toString => Stopped
        case v if v == Finished.toString => Finished
      }
    }
  }

 implicit val pickUpJF : JsonFormat[TweetPickUp] = jsonFormat5(TweetPickUp.apply)

}
