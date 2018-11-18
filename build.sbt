name := "scala-completion-engine"

version := "0.1"

scalaVersion := "2.12.7"

fork := true

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.12.7"

libraryDependencies += "org.scala-lang" % "scala-library" % "2.12.7"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.12.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.5",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5" % Test
)
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.18",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.18" % Test
)
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.5.18",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.18" % Test
)
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
