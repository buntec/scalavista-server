package org.scalavista


import spray.json._
import DefaultJsonProtocol._

import scala.util.{Try, Success, Failure}
import java.io.{File => JavaFile}

import better.files._
import coursier._
import coursier.parse.DependencyParser

object Launcher {

  def main(args: Array[String]): Unit = {

    // parse cli options
    val conf = new LauncherCliConf(args.toIndexedSeq)
    val port = conf.port()
    val uuid = conf.uuid()
    val pedantic = conf.pedantic()

    val logger =
      if (conf.trace())
        Logger(Logger.Trace)
      else if (conf.debug())
        Logger(Logger.Debug)
      else
        Logger(Logger.Info)

    logger.info("Launching scalavista server...")

    val preClasspath = System.getProperty("java.class.path")
    val classPathSeparator = JavaFile.pathSeparator //if (System.getProperty("os.name").startsWith("Windows")) ";" else ":"
    val scalaVersion = scala.util.Properties.versionNumberString

    lazy val defaultScalacOptions = {
          if (pedantic)
            CompilerOptions.pedantic
          else
            CompilerOptions.default
    }

    // manual dependencies
    lazy val libJars = {
        val libFolder = file"./lib"
        if (libFolder.isDirectory)
            libFolder.list
              .filter(f => f.extension == Some(".jar"))
              .map(f => f.pathAsString)
              .toList
          else
            List()
    }

    // try to read scalavista.json if present
    val jsonTry = Try {
      val file = file"scalavista.json"
      JsonParser(file.contentAsString).asJsObject
    }

    val (classPath, scalacOptions) = jsonTry match {
      case Success(json) =>
        logger.debug("Succesfully loaded scalavista.json")
        val userClasspath = json.fields.get("classpath").map(_.convertTo[List[String]]).getOrElse(Nil)
        val scalacOptions = json.fields.get("scalacOptions").map(_.convertTo[List[String]]).getOrElse(defaultScalacOptions)
        val dependencyClasspath = {
            val depStrings = json.fields.get("dependencies").map(_.convertTo[List[String]]).getOrElse(Nil)
            val deps = depStrings.flatMap(d => DependencyParser.dependency(d, scalaVersion).right.toSeq)
            logger.info("Fetching dependencies...")
            val artifacts = Fetch().addDependencies(deps: _*).run
            logger.info("Done.")
            artifacts.map(_.getAbsolutePath).toList
        }
        val classpath = (preClasspath :: (userClasspath ::: dependencyClasspath)).mkString(classPathSeparator)
        (classpath, scalacOptions)
      case Failure(_) =>
        logger.info("Could not find scalavista.json - proceeding without.")
        val classpath = (preClasspath :: libJars).mkString(classPathSeparator)
        val scalacOptions = defaultScalacOptions
        (classpath, scalacOptions)
    }


    lazy val allSources = file".".listRecursively
          .filter(f => f.extension == Some(".scala") || f.extension == Some(".java"))
          .map(_.pathAsString)
          .toList

    val sources = jsonTry match {
      case Success(json) =>
        json.fields.get("sources").map(_.convertTo[List[String]]).getOrElse(allSources)
      case Failure(_) => allSources
    }

    logger.debug(s"Sources to load: ${sources.mkString(",")}")
    logger.debug(s"starting server with uuid ${uuid}, listening on port ${port}")
    logger.debug(s"server classpath: ${classPath}")
    logger.debug(s"scalac options: ${scalacOptions.mkString(", ")}")

    val engine = Engine(classPath, scalacOptions, logger)

    val sourceMap = sources.map(sf => (sf, File(sf).contentAsString)).toMap
    engine.reloadFiles(sourceMap)

    Server.run(engine, port, uuid, logger)

    logger.info("Press any key to shut down.")
    scala.io.StdIn.readLine()
    System.exit(0)

  }
}
