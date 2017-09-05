name := MyBuild.NamePrefix + "search"

mainClass in (Compile, run) := Some("GatheringService")


mergeStrategy in assembly := {

  case PathList("org",
  "apache",
  "spark", "unused",
  "org/objectweb/asm/*",
  "SignatureReader.class",
  "SignatureWriter.class",
  "UnusedStubClass.class")
  => MergeStrategy.first

  case x => (mergeStrategy in assembly).value(x)

}