package com.ita.classifier

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.ita.classifier.controllers.ClassifierController
import com.ita.classifier.kafka.ConsumerTweets
import com.ita.domain.utils.{Config, Logger}
import org.apache.spark.{SparkConf, SparkContext}
import routes.ApiActor
import spray.can.Http
import utils.ClassifierDataContext

import scala.concurrent.duration._

object ClassifierService  extends App
  with Logger
  with Config{

  val hostApi = config.getString("service.host")
  val portApi = config.getInt("service.port")

  val sparkAppNme = config.getString("spark.app-name")
  val sparkMaster = config.getString("spark.master")

  //timeout needs to be set as an implicit val for the ask method (?)

  //start a new HTTP server on port 8080 with apiActor as the handler
  val data = Array(1, 2, 3, 4, 5)

  implicit val system = ActorSystem("classifier-service")
  val execService: ExecutorService = Executors.newCachedThreadPool()
  // val sourceTweets  = Source.actorPublisher[TweetInfo](,TweetInfo))
  implicit val conf = new SparkConf().setMaster("local[4]").setAppName(sparkAppNme)
  implicit val sc = new SparkContext("local", "sparkAppNme")
  implicit val dataContext:ClassifierDataContext =ClassifierDataContext.chargeFromConfig()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(10.seconds)

  val pickUpController =  ClassifierController()
  val apiActor = system.actorOf(Props(new ApiActor("api-classifier",pickUpController)),"api-actor")
  IO(Http) ? Http.Bind(apiActor, interface = hostApi, port = portApi)
  val topic = config.getString("kafka.consumer.topic")
  val groupId = config.getString("kafka.consumer.groupid")
  val brokers = config.getString("kafka.consumer.brokers")

  val kafkaCons:ConsumerTweets =  new ConsumerTweets(brokers,groupId,topic)
  kafkaCons.run()

}
