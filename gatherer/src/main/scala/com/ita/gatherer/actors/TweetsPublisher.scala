package com.ita.gatherer.actors

/**
  * Created by mikelwyred on 20/06/2017.
  */

import java.util.Properties

import akka.stream.actor.ActorPublisher
import com.ita.domain.{Id, TweetInfo}
import com.ita.domain.utils.{Config, Logger}
import com.ita.gatherer.utils.GathererDataContext
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.util.Random

class TweetsPublisher(implicit dataContext:GathererDataContext ) extends ActorPublisher[TweetInfo]
with Config
with Logger{

  val kafkaProducer:KafkaProducer[String,String] = createKafkaProducier
  val listIds = getTopicsRTDC
  val topic = config.getString("kafka.producer.topic")

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[TweetInfo] )
  }

  def createKafkaProducier:KafkaProducer[String,String] = {
    val events =config.getInt("kafka.producer.events")
    val topic = config.getString("kafka.producer.topic")
    val brokers = config.getString("kafka.producer.brokers")
    val rnd = new Random()
    val props = new Properties()
    props.put("bootstrap.servers", brokers)
    props.put("client.id", "ScalaProducerExample")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    new KafkaProducer[String, String](props)

  }

  def getTopicsRTDC:List[Id] = {
    dataContext.pickupDAO.getRTDC().get
  }



  override def receive: Receive = {
  case s: TweetInfo => {
  println(s"tweet recibido: ${s.tweetText}")
    dataContext.tweetsDAO.create(s)
      val data = new ProducerRecord[String, String](topic, s.topic.value, s.tweetText)
      //async
      //producer.send(data, (m,e) => {})
      //sync
      kafkaProducer.send(data)
  if (isActive && totalDemand > 0) onNext(s)

}
  case _ =>
    println(s"Nada recibido")

  }

  override def postStop(): Unit = {
  context.system.eventStream.unsubscribe(self)
}

}
object TweetsPublisher {

  def apply()(implicit dataContext: GathererDataContext): TweetsPublisher =
    new TweetsPublisher
}
