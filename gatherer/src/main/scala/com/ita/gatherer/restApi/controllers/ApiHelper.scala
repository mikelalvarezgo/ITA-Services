package com.ita.gatherer.restApi.controllers

import com.ita.domain.Id
import com.ita.domain.gatherer.{Created, TweetPickUp}
import com.ita.domain.Model._
import com.ita.classifier
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3, _}
import spray.json.RootJsonFormat
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
