import sbt._
import Keys._

object Dependencies {

  val slf4jVersion = "1.6.4"
  val slf4jNop = "org.slf4j" % "slf4j-nop" % slf4jVersion
  val casbahV = "3.1.1"
  val sprayVersion = "1.3.2"
  val sparkVersion = "1.6.0"
  val AkkaHttpVersion = "10.0.6"
  val akkaV = "2.4.4"

  val commonDependencies: Seq[ModuleID] = Seq(
    "com.vdurmont"   % "emoji-java" % "3.2.0",
    "org.spire-math" %% "cats" % "0.3.0",
    "com.typesafe" % "config" % "1.3.1",
    "io.spray" %% "spray-client" % "1.3.3",
    "org.apache.spark" %% "spark-streaming-twitter" %sparkVersion ,
    "org.mongodb" %% "casbah-commons" % casbahV,
    "org.mongodb" %% "casbah-core" % casbahV,
    "org.mongodb" %% "casbah-query" % casbahV,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-routing-shapeless2" % sprayVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "io.spray" %% "spray-json" % sprayVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV)
    /*.map(_.exclude("org.objectweb.asm", "org.objectweb.asm").
      exclude("org.eclipse.jetty.orbit", "javax.servlet").
      exclude("org.eclipse.jetty.orbit", "javax.transaction").
      exclude("org.eclipse.jetty.orbit", "javax.mail").
      exclude("org.eclipse.jetty.orbit", "javax.activation").
      exclude("commons-beanutils", "commons-beanutils-core").
      exclude("commons-collections", "commons-collections").
      exclude("commons-collections", "commons-collections").
      exclude("com.esotericsoftware.minlog", "minlog"))*/




  // https://mvnrepository.com/artifact/org.apache.tika/tika-core)



  val apiDependencies: Seq[ModuleID] = commonDependencies
  val domainDependencies: Seq[ModuleID] = commonDependencies


  val common2Dependencies: Seq[ModuleID] = Seq(
    "io.spray" %% "spray-json" % sprayVersion,
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-routing-shapeless2" % sprayVersion,
    "com.vdurmont"   % "emoji-java" % "3.2.0",
    "com.typesafe" % "config" % "1.3.1",
    "org.mongodb" %% "casbah-commons" % casbahV,
    "org.mongodb" %% "casbah-core" % casbahV,
    "org.mongodb" %% "casbah-query" % casbahV)
    // https://mvnrepository.com/artifact/org.apache.tika/tika-core)

// akka fuera NO
  // SPRAY ....NO
  // slf4j ??? NO
// scalatic etc


  val clientDependencies: Seq[ModuleID] =  commonDependencies ++ Seq(
    "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0",
    "org.apache.opennlp" % "opennlp-tools" % "1.6.0",
    "org.apache.opennlp" % "opennlp-uima" % "1.6.0",
    "org.mongodb.spark" %% "mongo-spark-connector" % "1.1.0",
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.apache.spark" %% "spark-core" % sparkVersion)
  val gathererDependencies: Seq[ModuleID] = commonDependencies ++ Seq(

    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "org.twitter4j" % "twitter4j-core" % "4.0.6",
    "org.apache.tika" % "tika-core" % "1.10",
    "com.optimaize.languagedetector" % "language-detector" % "0.5"
  ).map(_.exclude("org.objectweb.asm", "org.objectweb.asm"))

  val classifierDependencies: Seq[ModuleID] = commonDependencies ++ Seq(
    "org.mongodb.spark" %% "mongo-spark-connector" % "1.1.0",
    "org.apache.spark" %% "spark-mllib" % sparkVersion,
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0",
    "org.apache.opennlp" % "opennlp-tools" % "1.6.0",
    "org.apache.opennlp" % "opennlp-uima" % "1.6.0",
    "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
    "com.sksamuel.elastic4s" %% "elastic4s-testkit" % "1.7.0" % "test"
  )/*.map(_.exclude("org.objectweb.asm", "org.objectweb.asm").
    exclude("org.eclipse.jetty.orbit", "javax.servlet").
    exclude("org.eclipse.jetty.orbit", "javax.transaction").
    exclude("org.eclipse.jetty.orbit", "javax.mail").
    exclude("org.eclipse.jetty.orbit", "javax.activation").
    exclude("commons-beanutils", "commons-beanutils-core").
    exclude("commons-collections", "commons-collections").
    exclude("commons-collections", "commons-collections").
    exclude("com.esotericsoftware.minlog", "minlog"))*/


  val sparkDependencies: Seq[ModuleID] = commonDependencies
  val webDependencies: Seq[ModuleID] = commonDependencies
}
