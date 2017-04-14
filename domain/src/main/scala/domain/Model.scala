package domain

import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat9}
import spray.json._
import spray.json.DefaultJsonProtocol._


object Model {
  implicit val JFid = new JsonFormat[Id] {
    override def write(obj: Id): JsValue =
      JsObject("$oid" -> JsString(obj.value))

    override def read(json: JsValue): Id = {
      val JsString(value) = json.asJsObject.fields("$oid")
      Id(value)
    }
  }

  implicit val JFLocation:JsonFormat[Location] = jsonFormat2(Location.apply)

  implicit val JFTweetInfo:JsonFormat[TweetInfo]  =jsonFormat9(TweetInfo.apply)


}
