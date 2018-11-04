name := "scala-completion-engine"

version := "0.1"

scalaVersion := "2.12.7"

fork := true

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.12.7"

libraryDependencies += "org.scala-lang" % "scala-library" % "2.12.7"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.12.7"

libraryDependencies += "net.sf.py4j" % "py4j" % "0.10.8.1"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
