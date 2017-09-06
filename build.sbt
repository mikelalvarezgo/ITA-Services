import sbt._
import Keys._
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
).
  settings(
      mainClass in assembly := Some("ClassifierService"),
      assemblyJarName in assembly := "classifier.jar"
      // more settings here ...
  )

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
    dependencyOverrides += "joda-time" % "joda-time" % "2.8.2",
    dependencyOverrides += "spark-streaming-twitter_2.11" % "spark-streaming-twitter_2.11" % "1.6.0"

    // more settings here ...
  )
lazy val root = (project in file(".")).

  aggregate(common, domain,gatherer, classifier)

