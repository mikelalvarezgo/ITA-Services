name := MyBuild.NamePrefix + "classifier"

mainClass in (Compile, run) := Some("ClassifierService")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
