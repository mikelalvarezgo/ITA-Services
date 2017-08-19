package results

import domain.{Id, TweetInfo}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import com.mongodb.spark._
import com.mongodb.spark.config.ReadConfig
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.functions.{max, min}
import org.apache.spark.{SparkConf, SparkContext}
import org.bson.Document
import utils.{Config, Logger}
/**
  * Created by mikelwyred on 19/08/2017.
  */
case class ModelExecution(
  _id: Id,
  modelId: Id,
  topicId: Id,
  nTweetsAnalize: Int,
  dateExecution: Long,
  conf: SparkConf) extends Config with Logger{

  def runModel(tweets:RDD[TweetInfo]):RDD[TweetResult] = {
    tweets.map{tweet =>
      logger.info(s"[MODEL-API] $modelId")

    }
  }

  def extractData():RDD[TweetInfo] ={

    val sc = new SparkContext(conf)
    val readConfig = ReadConfig(Map("uri" -> "mongodb://127.0.0.1/", "database" -> "test", "collection" -> "zips")) // 1)
    sc.loadFromMongoDB(readConfig).filter(tw =>
      tw.get[Id]("topic", classOf[Id]) == topicId).map(doc => doc.asInstanceOf[TweetInfo]) // 2)
  }

}
