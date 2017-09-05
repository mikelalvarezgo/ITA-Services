package com.ita.common.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}

import scala.concurrent.Await
import scala.util.Try

/**
  * Created by mikelwyred on 19/06/2017.
  */

trait HttpClient {

  val serviceName: String
  val baseUrl: String

  val httpTimeout = {
    import scala.concurrent.duration._
    5.seconds
  }

  implicit lazy val system = ActorSystem(s"$serviceName-client-system")
  implicit lazy val materializer: ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(system))
  lazy val http = Http(system)

  def isOnline: Boolean = {
    import system.dispatcher
    Try(Await.result(
      http.singleRequest(
        HttpRequest(uri = s"$baseUrl/version")).map(_.status == StatusCodes.OK),
      httpTimeout)).getOrElse(false)
  }

}
