package domain

import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import scala.util.Try
import twitter4j.Status
case class TweetInfo(
  _id: Option[Id],
  tweet_id: Long,
  createdAt: Long,
  latitude: Option[Location],
  user_id: Long,
  user_name: String,
  user_followers: Int,
  tweetText: String,
  lenguage: String)

case class Location(
  latitude: Double,
  longitude:Double
)
object Location {
  type Latitude = Double
  type Longitude = Double
}

object TweetInfo extends {
  def content2TwitterInfo(tw: Status,leng: String): Try[TweetInfo] = Try {
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
      leng)
  }
}


