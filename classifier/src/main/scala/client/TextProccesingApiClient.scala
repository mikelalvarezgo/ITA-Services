package client

import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import domain.Id
import rest.HttpClient
import spray.json.DefaultJsonProtocol.{jsonFormat2, jsonFormat3}

import scala.concurrent.Future
import scala.util.Try

/**
  * Created by mikelwyred on 19/06/2017.
  */
case class TextProccesingApiClient(
  serviceName: String,
  baseUrl:String = "http://text-processing.com/api/sentiment/")
  extends {

  case class Probs(
    neg:Double,
    neutral:Double,
    pos: Double
  )
  case class ClientResponse(
    probability:Probs,
    label: String)
  case class ClientPayLoad(
    text: String
  )
  def AnalizeText(textString:String):Future[ClientResponse] = {
    implicit val probsJF = jsonFormat3(Probs.apply)
    implicit  val clientResponseJF = jsonFormat2(ClientResponse.apply)
    val formData = FormData(Map("text"-> textString))
    val content = for {
      request <- Marshal(formData).to[RequestEntity]
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = baseUrl,
        entity = request))
      entity <- Unmarshal(response.entity).to[ClientResponse]
    } yield entity
    content
  }
}