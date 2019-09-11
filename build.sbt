lazy val scalavista = (project in file("."))
    .settings(
      name := "scalavista-server",
      version := "0.2.0",
      scalaVersion := "2.13.0",
      crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.0"),
      fork := true,
      scalacOptions ++= Seq(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Ywarn-dead-code",
        "-Ywarn-value-discard",
        "-Xlint:_"
      ),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-library" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.typesafe.akka" %% "akka-http" % "10.1.9",
        "com.typesafe.akka" %% "akka-actor" % "2.5.23",
        "com.typesafe.akka" %% "akka-stream" % "2.5.23",
        "io.spray" %% "spray-json" % "1.3.5",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.9",
        "com.github.pathikrit" %% "better-files" % "3.8.0",
        "org.rogach" %% "scallop" % "3.3.1",
        "io.get-coursier" %% "coursier" % "2.0.0-RC3-3"
      ),
      mainClass in assembly := Some("org.scalavista.Launcher"),
      assemblyJarName in assembly := s"${name.value}-${version.value}_${scalaBinaryVersion.value}.jar",

      assemblyMergeStrategy in assembly := {
        case PathList(ps @ _*) if ps.last contains "jansi" => MergeStrategy.first
        case x =>
            val oldStrategy = (assemblyMergeStrategy in assembly).value
            oldStrategy(x)
        }

    )
