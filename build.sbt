name := "maze"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

val akkaVersion = "2.5.16"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-stream"              % akkaVersion,
  "com.typesafe.akka"        %% "akka-slf4j"               % akkaVersion,
  "ch.qos.logback"           % "logback-classic"           % "1.2.3",
  "com.typesafe.akka"        %% "akka-stream-testkit"      % akkaVersion % Test,
  "org.scalatest"            %% "scalatest"                % "3.0.6" % Test
)

parallelExecution in Test := false