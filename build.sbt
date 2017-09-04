import sbt._
import Keys._
import complete.DefaultParsers.spaceDelimited

name := MyBuild.NamePrefix + "root"

version := "0.0.1"
scalaVersion := "2.11.7"
fork := true

resolvers += "AkkaRepository" at "http://repo.akka.io/releases/"

lazy val common = project.
  dependsOn(domain).
settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.commonDependencies)

lazy val api = project.
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.apiDependencies)

lazy val client = project.
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.clientDependencies)

excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
lazy val domain = project.
  dependsOn().
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.domainDependencies)

lazy val gatherer = (project in file("./gatherer")).
  dependsOn(api, common, domain).
  settings(Common.settings: _*).
  settings( name := "myapp-config",
    includeFilter in Compile := "application.conf").
  settings(libraryDependencies ++= Dependencies.gathererDependencies,
    excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm",
    dependencyOverrides += "joda-time" % "joda-time" % "2.8.2"

  ).
  settings(
      mainClass in assembly := Some("GatheringService"),
      assemblyJarName in assembly := "gatherer.jar"
      // more settings here ...
  )

lazy val classifier = project.
  dependsOn(common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.classifierDependencies,
    excludeDependencies += "org.objectweb.asm" % "org.objectweb.asm"
).
  settings(
      mainClass in assembly := Some("ClassifierService"),
      assemblyJarName in assembly := "classifier.jar"
      // more settings here ...
  )

lazy val root = (project in file(".")).

  aggregate(common,client, domain,gatherer, classifier)

