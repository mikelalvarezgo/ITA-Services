package restApi.routes

import domain.Id
import domain.gatherer.{Created, TweetPickUp}
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3}
import spray.json.DefaultJsonProtocol._
import domain.Model._
/**
  * Created by mikelalvarezgo on 19/6/17.
  */
case class CreatePickUpPayloadRequest(
  topics:List[String],
  cuantity_warn:Long) {
  def converToTweetPickUp:TweetPickUp =
    TweetPickUp(
      None,
      topics = this.topics,
      cuantity_warn = this.cuantity_warn,
      nEmojis = 0,
      nTweets = 0,
      state = Created)
}

case class UpdatePickUpPayloadRequest(
  _id: Id,
  topics:List[String],
  cuantity_warn:Long)
object ApiHelper {
  implicit val JFCreatePickUpPayloadRequest:RootJsonFormat[CreatePickUpPayloadRequest]
  = jsonFormat2(CreatePickUpPayloadRequest.apply)
  implicit val JFUpdatePickUpPayloadRequest:RootJsonFormat[UpdatePickUpPayloadRequest]
  = jsonFormat3(UpdatePickUpPayloadRequest.apply)



}
