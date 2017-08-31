package classifier

import com.fasterxml.jackson.annotation.JsonValue
import models._
import results.{ModelExecution, ModelResult, TweetResult}
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat9}
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport
import spray.json._
import client._
import domain.Model._

object Model {

  implicit val modelTypeJF: JsonFormat[ModelType] =
    new JsonFormat[ModelType] {

      def read(json: JsValue): ModelType = {
        val JsString(value) = json
        value match {
          case v if v == API.toString => API
          case v if v == BOOSTING.toString => BOOSTING
          case v if v == NLP.toString => NLP
          case v if v == BAYES.toString => BAYES

        }
      }

      def write(obj: ModelType): JsValue = JsString(obj.toString)
    }


  implicit  val probsJF :RootJsonFormat[Probs] = jsonFormat3(Probs.apply)

  implicit  val responseJF:RootJsonFormat[ClientResponse] = jsonFormat2(ClientResponse.apply)

  implicit val tweetResultJF:RootJsonFormat[TweetResult] = jsonFormat5(TweetResult.apply)

  implicit val partitionConfJF:RootJsonFormat[PartitionConf]= jsonFormat3(PartitionConf.apply)

  implicit val modelResultJF:RootJsonFormat[ModelResult] = jsonFormat10(ModelResult.apply)

  implicit val executionJF:RootJsonFormat[ModelExecution] = jsonFormat7(ModelExecution.apply)

  implicit val modelJF:RootJsonFormat[ModelData] =  jsonFormat7(ModelData.apply)

}
