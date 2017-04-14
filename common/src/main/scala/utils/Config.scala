package utils

import com.typesafe.config.ConfigFactory

trait Config {

  lazy val config = ConfigFactory.load("application.conf")

}

object Config {

  val SparkMaster = "spark-twitter.spark.master"
  val AppName = "spark-twitter.spark.app-name"
  val WindowSeconds = "spark-twitter.window-seconds"
  val CassandraHost = "cassandra.host"
  val CassandraKeySpace = "cassandra.keyspace"


}
