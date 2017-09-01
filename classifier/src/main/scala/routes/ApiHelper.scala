package routes

import domain.Id
import domain.Model._
import domain.gatherer.{Created, TweetPickUp}
import results.ModelExecution
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3, _}
import spray.json.RootJsonFormat



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


case class ModelExecutionPayload(
  _id:Option[Id],
  topic_id:String,
  model_id:String) {
  import org.joda.time.DateTime._
  def toExecution(id:Id):ModelExecution =
    ModelExecution(Some(id),Id(topic_id),Id(model_id),org.joda.time.DateTime.now.getMillis,"created")

}

object ApiHelper {
  implicit val JFCreatePickUpPayloadRequest:RootJsonFormat[ModelExecutionPayload]
  = jsonFormat3(ModelExecutionPayload.apply)


}
