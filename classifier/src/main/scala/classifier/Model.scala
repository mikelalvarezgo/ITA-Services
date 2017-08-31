package classifier

import models._
import results.{ModelExecution, ModelResult, TweetResult}
import spray.json.DefaultJsonProtocol._
import client._
import domain.Model._
import spray.json._

object Model {

  implicit val modelTypeJF: RootJsonFormat[ModelType] =
    new RootJsonFormat[ModelType] {

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

  implicit val executionJF:RootJsonFormat[ModelExecution] = new  RootJsonFormat[ModelExecution]{
    override def write(obj: ModelExecution): JsValue =
      Json
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

  implicit val modelJF:RootJsonFormat[ModelData] =  jsonFormat7(ModelData.apply)

}
