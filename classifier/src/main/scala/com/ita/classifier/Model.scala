package com.ita.classifier

import models._
import results.{ModelExecution, ModelResult, TweetResult}
import spray.json.DefaultJsonProtocol._
import client._
import com.ita.domain.Id
import com.ita.domain.Model._
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

  implicit val modelResultJF:RootJsonFormat[ModelResult] = jsonFormat20(ModelResult.apply)

  implicit val executionJF:RootJsonFormat[ModelExecution] = new  RootJsonFormat[ModelExecution] {
    override def write(obj: ModelExecution): JsValue =
      JsObject(Map(
        "_id" ->
          obj._id.map(_.toJson).getOrElse(JsNull),
        "modelId" -> obj.modelId.toJson,
        "topicId" -> obj.topicId.toJson,
        "dateExecution" -> JsNumber(obj.dateExecution),
        "resultModel" -> obj.resultModel.map(_.toJson).getOrElse(JsString("")),
        "status" -> JsString(obj.status)))

    override def read(json: JsValue): ModelExecution = {
      val JsObject(atts) = json
      val JsNumber(dateExecution) = atts("dateExecution")
      val JsObject(ola) = atts("modelId")
      val modelId = atts("modelId").convertTo[Id]
      val topicId = atts("topicId").convertTo[Id]
      val id = if (atts.isDefinedAt("_id")) Some(atts("_id").convertTo[Id]) else None
      val result:Option[ModelResult] =  (atts("resultModel")) match {
        case JsString("") |JsNull => None
        case _ =>  Some((atts("resultModel")).convertTo[ModelResult])
      }

      val JsString(status) = atts("status")
      ModelExecution(id, modelId, topicId, dateExecution.toLong, status,result)
    }
  }

  implicit val modelJF:RootJsonFormat[ModelData] =  jsonFormat6(ModelData.apply)

}
