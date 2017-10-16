import sbt._
import Keys.{excludeDependencies, _}
import complete.DefaultParsers.spaceDelimited
organization := "com.ita"

name := MyBuild.NamePrefix + "root"

version := "0.0.1"
scalaVersion := "2.11.7"
fork := true

resolvers += "AkkaRepository" at "http://repo.akka.io/releases/"


lazy val domain = (project in file("domain")).
  dependsOn().
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.domainDependencies)


lazy val common = (project in file("common")).
  dependsOn(domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.commonDependencies)


/*lazy val gatherer = (project in file("gatherer")).
  dependsOn(common, domain).
  settings(Common.settings: _*).
  settings( name := "myapp-config",
    includeFilter in Compile := "application.conf").
  settings(libraryDependencies ++= Dependencies.gathererDependencies,
    organization in ThisBuild := "com.ita.gatherer",
    fullClasspath in assembly := (fullClasspath in Compile).value,
      assemblyMergeStrategy in assembly := {
      case PathList(xs @ _*) if xs.last == "UnusedStubClass.class" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
      excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm",
    dependencyOverrides += "joda-time" % "joda-time" % "2.8.2",
    dependencyOverrides += "spark-streaming-twitter_2.11" % "spark-streaming-twitter_2.11" % "1.6.0"

  ).
  settings(
      mainClass in assembly := Some("GatheringService"),
      assemblyJarName in assembly := "gatherer.jar"
      // more settings here ...
  )*/

lazy val classifier = (project in file("classifier")).
  dependsOn(common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.classifierDependencies
    .map(_.exclude("org.objectweb.asm", "org.objectweb.asm"))).
  settings(
      mainClass in assembly := Some("com.ita.classifier.ClassifierService"),
      assemblyJarName in assembly := "classifier.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last == "UnusedStubClass.class" =>
        MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "overview.html" =>
        MergeStrategy.first
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("org", "objectweb", xs @ _*) => MergeStrategy.last
      case PathList("javax", "xml", xs @ _*) => MergeStrategy.last
      case PathList("org", "joda", xs @ _*) => MergeStrategy.last
      case PathList("com", "google", xs @ _*) => MergeStrategy.last
      case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
      case PathList("org", "aopalliance", xs @ _*) => MergeStrategy.last
      case PathList("commons-beanutils", "commons-beanutils", xs @ _*) => MergeStrategy.last
      case PathList("org.codehaus.janino", xs @ _*) => MergeStrategy.last

      case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    })

lazy val gatherer = (project in file("gatherer")).
  dependsOn(common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.gathererDependencies
  ).
  settings(
    mainClass in assembly := Some("com.ita.gatherer.GatheringService"),
    assemblyJarName in assembly := "gatherer.jar",
    organization in ThisBuild := "com.ita.gatherer",
    fullClasspath in assembly := (fullClasspath in Compile).value,
    assemblyMergeStrategy in assembly := {
      case PathList(xs @ _*) if xs.last == "UnusedStubClass.class" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm",
    dependencyOverrides += "joda-time" % "joda-time" % "2.8.2"

    // more settings here ...
  )

lazy val visualizer = (project in file("visualizer")).
  dependsOn(common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.visualizerDependencies
  ).
  settings(
    mainClass in assembly := Some("com.ita.gatherer.VisualizerService"),
    assemblyJarName in assembly := "visualizer.jar",
    organization in ThisBuild := "com.ita.visualizer",
    fullClasspath in assembly := (fullClasspath in Compile).value,
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList(xs @ _*) if xs.last == "UnusedStubClass.class" =>
        MergeStrategy.first
      case PathList(xs @ _*) if xs.last == "overview.html" =>
        MergeStrategy.first
      case PathList("org", "apache", xs @ _*) => MergeStrategy.last
      case PathList("org", "objectweb", xs @ _*) => MergeStrategy.last
      case PathList("javax", "xml", xs @ _*) => MergeStrategy.last
      case PathList("org", "joda", xs @ _*) => MergeStrategy.last
      case PathList("com", "google", xs @ _*) => MergeStrategy.last
      case PathList("javax", "inject", xs @ _*) => MergeStrategy.last
      case PathList("org", "aopalliance", xs @ _*) => MergeStrategy.last
      case PathList("commons-beanutils", "commons-beanutils", xs @ _*) => MergeStrategy.last
      case PathList("org.codehaus.janino", xs @ _*) => MergeStrategy.last

      case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm",
    dependencyOverrides += "joda-time" % "joda-time" % "2.8.2"

    // more settings here ...
  )
lazy val root = (project in file(".")).

  aggregate(common, domain,gatherer, classifier, visualizer)

