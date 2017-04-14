import sbt._
import Keys._

object Dependencies {

  val slf4jVersion = "1.6.4"
  val slf4jNop = "org.slf4j" % "slf4j-nop" % slf4jVersion
  val casbahV = "3.1.1"
  val sprayVersion = "1.2.2"
  val sparkVersion = "1.2.1"

  val commonDependencies: Seq[ModuleID] = Seq(
    "org.spire-math" %% "cats" % "0.3.0",
    "org.scalaz" %% "scalaz-core" % "7.2.0",
    "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.0",
    "com.typesafe.akka" %% "akka-actor" % "2.3.2",
    "com.etaty.rediscala" %% "rediscala" % "1.3.1",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" % "scalatest_2.11" % "3.0.1",
    "io.spray" %% "spray-client" % "1.3.3",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-twitter" % sparkVersion,
    "org.slf4j" % "slf4j-simple" % "1.7.5",
    "org.mongodb" %% "casbah-commons" % casbahV,
    "org.mongodb" %% "casbah-core" % casbahV,
    "org.mongodb" %% "casbah-query" % casbahV,
    "io.spray" % "spray-json_2.11" % "1.3.2",
    // https://mvnrepository.com/artifact/org.apache.tika/tika-core
    "org.apache.tika" % "tika-core" % "1.10",
    "com.optimaize.languagedetector" % "language-detector" % "0.5")


  val json: Seq[ModuleID] = Seq(
    "io.argonaut" %% "argonaut" % "6.0.4",
    "com.propensive" %% "rapture-json-argonaut" % "1.1.0",
    "com.typesafe.play" %% "play-json" % "2.4.2")

  val apiDependencies: Seq[ModuleID] = commonDependencies
  val domainDependencies: Seq[ModuleID] = commonDependencies
  val clientDependencies: Seq[ModuleID] = commonDependencies
  val gathererDependencies: Seq[ModuleID] = commonDependencies ++ Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-twitter" % sparkVersion,
    "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" % "1.7.0" % "test"
  )
  val sparkDependencies: Seq[ModuleID] = commonDependencies ++ Seq(
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion)
  val webDependencies: Seq[ModuleID] = commonDependencies ++ json ++ {
    Seq(
      //jdbc,
      //cache,
      // ws
      //specs2 % Test
    )

  }
}
