package com.ita.domain

import com.vdurmont.emoji._
import twitter4j.Status

import scala.util.Try
case class TweetInfo(
  _id: Option[Id],
  tweet_id: Long,
  createdAt: Long,
  latitude: Option[Location],
  user_id: Long,
  user_name: String,
  user_followers: Int,
  tweetText: String,
  lenguage: String,
  contain_emoji: Boolean,
  topic: Id)

case class Location(
  latitude: Double,
  longitude:Double
)
object Location {
  type Latitude = Double
  type Longitude = Double
}

object TweetInfo extends {
  def containtTextEmojis(text: String): Boolean = {
    (text != EmojiParser.parseToAliases(text))
  }
  def content2TwitterInfo(tw: Status,leng: String, idTopic: Id): Try[TweetInfo] = Try {
    val location = Try{
      val tweetLoc = tw.getGeoLocation
      Location(tweetLoc.getLatitude, tweetLoc.getLongitude)
    }.toOption
    TweetInfo(
      None,
      tw.getId,
      tw.getCreatedAt.getTime,
      location,
      tw.getUser.getId,
      tw.getUser.getName,
      tw.getUser.getFollowersCount,
      tw.getText,
      leng,
      containtTextEmojis(tw.getText),
      idTopic)
  }
}


