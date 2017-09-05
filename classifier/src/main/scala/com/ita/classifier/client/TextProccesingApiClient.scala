package com.ita.classifier.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.ita.classifier.Model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
case class TextProccesingApiClient(
  serviceName: String,
  base_url:String = "http://text-processing.com/api/sentiment/")
  extends ApiClient{


  def AnalizeText(textString:String):Future[ClientResponse] = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val formData = FormData(Map("text"-> textString))
    val content = for {
      request <- Marshal(formData).to[RequestEntity]
      response <- Http()(system).singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = base_url,
        entity = request))
      entity <- Unmarshal(response.entity).to[ClientResponse]
    } yield entity
    content
  }
}