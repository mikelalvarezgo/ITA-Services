import sbt._
import Keys._

import scala.util.Try

object Common {
  val appVersion = "0.0.1"

  lazy val copyDependencies = TaskKey[Unit]("copy-dependencies")

  def copyDepTask = copyDependencies <<= (update, crossTarget, scalaVersion) map {
    (updateReport, out, scalaVer) =>
    updateReport.allFiles foreach { srcPath =>
      val destPath = out / "lib" / srcPath.getName
      IO.copyFile(srcPath, destPath, preserveLastModified=true)
    }
  }
  
  val settings: Seq[Def.Setting[_]] = Seq(
    version := appVersion,
    scalaVersion := "2.11.7",
    scalacOptions += "-target:jvm-1.8",
    resolvers += Opts.resolver.mavenLocalFile,
    copyDepTask,
    scalacOptions += "-target:jvm-1.8",
    resolvers ++= Seq(
      "mvnrepository" at "http://mvnrepository.com/artifact/",
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Maven" at "https://repo1.maven.org/maven2/",
      "JCenter" at "http://jcenter.bintray.com/"))



}
