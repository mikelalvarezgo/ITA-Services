package client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import domain.Id
import rest.HttpClient
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import classifier.Model._
import akka.http.scaladsl.marshalling._

import scala.concurrent.Future
import scala.util.Try
import spray.json._
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