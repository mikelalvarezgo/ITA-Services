import sbt._
import Keys._

object Dependencies {

  val slf4jVersion = "1.6.4"
  val slf4jNop = "org.slf4j" % "slf4j-nop" % slf4jVersion
  val casbahV = "3.1.1"
  val sprayVersion = "1.3.2"
  val sparkVersion = "2.2.0"
  val AkkaHttpVersion = "10.0.6"
  val akkaActorVersion = "2.4.19"

  val commonDependencies: Seq[ModuleID] = Seq(
    "com.lightbend" %% "emoji" % "1.1.1",
    "com.vdurmont"   % "emoji-java" % "3.2.0",
    "org.spire-math" %% "cats" % "0.3.0",
    "com.typesafe" % "config" % "1.3.1",
    "org.scalaz" %% "scalaz-core" % "7.2.0",
    "org.scalaz" %% "scalaz-scalacheck-binding" % "7.2.0",
    "com.typesafe.akka" %% "akka-actor" % akkaActorVersion,
    "com.etaty.rediscala" %% "rediscala" % "1.3.1",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "org.scalatest" % "scalatest_2.11" % "3.0.1",
    "io.spray" %% "spray-client" % "1.3.3",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "com.typesafe.akka" % "akka-http-experimental_2.11" % "2.4.10",
    "org.slf4j" % "slf4j-simple" % "1.7.5",
    "org.apache.spark" % "spark-streaming-twitter_2.10" % "1.6.2",
  "org.mongodb" %% "casbah-commons" % casbahV,
    "org.mongodb" %% "casbah-core" % casbahV,
    "org.mongodb" %% "casbah-query" % casbahV,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-routing-shapeless2" % sprayVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.8",
  "io.spray" %% "spray-json" % sprayVersion,
    "com.typesafe.akka" %%"akka-http" % AkkaHttpVersion,
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
    "org.twitter4j" % "twitter4j-core" % "4.0.6",
    "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" % "1.7.0" % "test"
  )
  val classifierDependencies: Seq[ModuleID] = commonDependencies ++ Seq(
    "org.mongodb.spark" % "mongo-spark-connector_2.11" % "2.2.0",
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0",
    "org.apache.opennlp" % "opennlp-tools" % "1.6.0",
    "org.apache.opennlp" % "opennlp-uima" % "1.6.0",
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
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
