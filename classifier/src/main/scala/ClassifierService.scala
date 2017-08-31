import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import controllers.ClassifierController
import org.apache.spark.{SparkConf, SparkContext}
import routes.ApiActor
import spray.can.Http
import utils.{ClassifierDataContext, Config, Logger}

import scala.concurrent.duration._

object GatheringService  extends App
  with Logger
  with Config{

  val hostApi = config.getString("service.host")
  val portApi = config.getInt("service.port")

  //timeout needs to be set as an implicit val for the ask method (?)

  //start a new HTTP server on port 8080 with apiActor as the handler
  implicit val system = ActorSystem("classifier-service")
  val execService: ExecutorService = Executors.newCachedThreadPool()
  // val sourceTweets  = Source.actorPublisher[TweetInfo](,TweetInfo))
  val conf = new SparkConf().setMaster("local").setAppName("My App")
  implicit val sc = new SparkContext(conf)
  implicit val dataContext:ClassifierDataContext =ClassifierDataContext.chargeFromConfig()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(10.seconds)
  val pickUpController =  ClassifierController()

  val apiActor = system.actorOf(Props(new ApiActor("api-classifier",pickUpController)),"api-actor")
  IO(Http) ? Http.Bind(apiActor, interface = hostApi, port = portApi)
}