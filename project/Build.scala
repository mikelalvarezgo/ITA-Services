import sbt._
import Keys._

object MyBuild extends Build {
  import Dependencies._
  import Common._


  val NamePrefix = "ITA-"



  resolvers
  def ITAService(
    name: String,
    dependsOn: Seq[ClasspathDep[ProjectReference]]): Project =
    Project(id = name, base = file(name), dependencies = dependsOn).settings(
      settings)

}