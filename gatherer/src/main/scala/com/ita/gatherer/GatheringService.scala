package com.ita.gatherer

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.ita.domain.TweetInfo
import com.ita.domain.utils.{Config, Logger}
import com.ita.gatherer.actors.TweetsPublisher
import com.ita.gatherer.restApi.controllers.{ApiActor, PickUpController}
import com.ita.gatherer.utils.GathererDataContext
import spray.can.Http

import scala.concurrent.duration._

object GatheringService  extends App
  with Logger
  with Config{
  logger.info(s"PATH CLASS ${this.getClass.getName().replace('.', '/')}.class")
  val hostApi = config.getString("service.host")
  val portApi = config.getInt("service.port")

  //timeout needs to be set as an implicit val for the ask method (?)

  //start a new HTTP server on port 8080 with apiActor as the handler
  implicit val system = ActorSystem("gathering-service")
  val execService: ExecutorService = Executors.newCachedThreadPool()

  implicit val dataContext:GathererDataContext =GathererDataContext.chargeFromConfig()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(10.seconds)
  val pickUpController =  PickUpController()
  val tweetsPublisher = system.actorOf(Props(new TweetsPublisher()),"tweetsPublisher")
  // val sourceTweets  = Source.actorPublisher[TweetInfo](,TweetInfo))
  system.eventStream.subscribe(tweetsPublisher, classOf[TweetInfo])

  val apiActor = system.actorOf(Props(new ApiActor("api-gathering",pickUpController)),"api-actor")
  IO(Http) ? Http.Bind(apiActor, interface = hostApi, port = portApi)
}
