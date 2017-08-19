name := MyBuild.NamePrefix + "root"

version := "0.0.1"

scalaVersion := "2.11.8"
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
lazy val common = project.
  dependsOn(domain).
settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.commonDependencies)

lazy val api = project.
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.apiDependencies)

lazy val client = project.
  dependsOn(api).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.clientDependencies)

lazy val domain = project.
  dependsOn().
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.domainDependencies)

lazy val gatherer = project.
  dependsOn(api, common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.gathererDependencies)

lazy val classifier = project.
  dependsOn(api, common, domain).
  settings(Common.settings: _*).
  settings(libraryDependencies ++= Dependencies.classifierDependencies)

lazy val root = (project in file(".")).
  aggregate(common, domain,gatherer)
