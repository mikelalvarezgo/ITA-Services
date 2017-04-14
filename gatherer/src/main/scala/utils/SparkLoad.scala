package utils

import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

trait SparkLoad  extends Actions with Filters{
  _: Config =>
  // Before all, we create the Spark context

  var masterConfig = "spark-twitter.spark.master"
  var nameConfig = "spark-twitter.spark.app-name"

  var sc: SparkContext = null

  type Content = twitter4j.Status

  val conf = new SparkConf().set("logConf","true")
    .setAppName(config.getString(Config.AppName))
    .setMaster(config.getString(Config.SparkMaster))
    .set("spark.cassandra.connection.host", Config.CassandraHost)


  val windowTime = Seconds(config.getInt(Config.WindowSeconds))

  val ssc = new StreamingContext(conf, windowTime)
  lazy val stream = TwitterUtils.createStream(ssc, None, filters)
}


