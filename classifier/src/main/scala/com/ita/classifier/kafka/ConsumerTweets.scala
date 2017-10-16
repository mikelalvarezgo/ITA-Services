package com.ita.classifier.kafka

import java.util.concurrent._
import java.util.{Collections, Properties}

import com.ita.classifier.models.{NLPAnalizer, Sentiment}
import com.ita.domain.utils.{Config, Logger}
import kafka.consumer.KafkaStream
import kafka.utils.Logging
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}

import scala.collection.JavaConversions._

class ConsumerTweets(val brokers: String,
  val groupId: String,
  val topic: String) extends Config with Logger {

  val props = createConsumerConfig(brokers, groupId)
  val consumer = new KafkaConsumer[String, String](props)
  var executor: ExecutorService = null

  def run() = {
    consumer.subscribe(Collections.singletonList(this.topic))
    Executors.newSingleThreadExecutor.execute(    new Runnable {
      override def run(): Unit = {
        while (true) {
          val records = consumer.poll(10)
          for (record <- records) {
             val score = NLPAnalizer.extractSentiment(record.value())

            logger.info(s"[TWEETS-CONSUMER] : topic  ${record.key()}  and text ${record.value()}  " +
              s"has SENTIMENT ${score} -- ${Sentiment.toInt(score)}" )
          }
        }
      }
    })
  }
}
