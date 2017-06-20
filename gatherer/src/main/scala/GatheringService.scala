import actors.{PickUpManager, TweetsPublisher}
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.stream.ActorMaterializer
import restApi.controllers.PickUpController
import restApi.routes.ApiActor
import utils.{Config, GathererDataContext, Logger}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.Timeout
import domain.TweetInfo
import spray.can.Http

import scala.concurrent.duration._

object GatheringService  extends App
with Logger
with Config{

  val hostApi = config.getString("service.host")
  val portApi = config.getInt("service.port")

  //timeout needs to be set as an implicit val for the ask method (?)

  //start a new HTTP server on port 8080 with apiActor as the handler
  implicit val system = ActorSystem("gathering-service")
  implicit val dataContext:GathererDataContext =GathererDataContext.chargeFromConfig()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(10.seconds)
  val pickUpController =  PickUpController()
  val sourceTweets  = Source.actorPublisher[TweetInfo](Props(TweetsPublisher()))
  val apiActor = system.actorOf(Props(new ApiActor("api-gathering",pickUpController)),"api-actor")
  IO(Http) ? Http.Bind(apiActor, interface = hostApi, port = portApi)
}
