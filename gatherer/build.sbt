name := MyBuild.NamePrefix + "search"

mainClass in (Compile, run) := Some("com.ita.gatherer.GatheringService")


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}