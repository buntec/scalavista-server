package org.scalavista

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import scala.sys.process._
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
    val serverUrl = s"http://localhost:$port"
    val debug = if (conf.debug()) "--debug" else ""
    val trace = if (conf.trace()) "--trace" else ""
    val pedantic = conf.pedantic()

    val logger =
      if (conf.trace())
        Logger(Logger.Trace)
      else if (conf.debug())
        Logger(Logger.Debug)
      else
        Logger(Logger.Info)


    val preClasspath = System.getProperty("java.class.path")
    val pathSeparator = System.getProperty("file.separator")
    val java = System.getProperty("java.home") + pathSeparator + "bin" + pathSeparator + "java"
    val className = "org.scalavista.Server"
    val classPathSeparator = JavaFile.pathSeparator //if (System.getProperty("os.name").startsWith("Windows")) ";" else ":"
    val scalaVersion = scala.util.Properties.versionNumberString

    def combineScalacOptions(options: Seq[String]): String = {
      options.mkString("w", "&#", "w")
    }

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

    // here we construct the command to start the server subprocess; we branch on whether or not we have a scalavista.json
    val cmd = jsonTry match {

      case Success(json) =>
        logger.debug("Succesfully loaded scalavista.json")
        val userClasspath = json.fields.get("classpath").map(_.convertTo[List[String]]).getOrElse(Nil)
        val scalacOptions = combineScalacOptions(
          json.fields.get("scalacOptions").map(_.convertTo[List[String]]).getOrElse(defaultScalacOptions))
        val dependencyClasspath = {
            val depStrings = json.fields.get("dependencies").map(_.convertTo[List[String]]).getOrElse(Nil)
            val deps = depStrings.flatMap(d => DependencyParser.dependency(d, scalaVersion).right.toSeq)
            val artifacts = Fetch().addDependencies(deps: _*).run
            artifacts.map(_.getAbsolutePath).toList
        }
        val classpath = (preClasspath :: userClasspath :: dependencyClasspath).mkString(classPathSeparator)
        s"$java -cp $classpath $className $debug $trace --uuid $uuid --port $port --scalacopts $scalacOptions"

      case Failure(_) =>
        logger.info("Could not find scalavista.json - proceeding without.")
        val classpath = (preClasspath :: libJars).mkString(classPathSeparator)
        val scalacOptions = combineScalacOptions(defaultScalacOptions)
        s"$java -cp $classpath $className $debug $trace --uuid $uuid --port $port --scalacopts $scalacOptions"
    }

    // spawn the server process
    logger.debug(cmd)
    logger.info("Launching server...")
    val serverProcess = Process(cmd).run

    // load all known Scala source files
    lazy val allSources = file".".listRecursively
          .filter(f => f.extension == Some(".scala") || f.extension == Some(".java"))
          .map(_.pathAsString)
          .toList
    val sources = jsonTry match {
      case Success(json) =>
        json.fields.get("sources").map(_.convertTo[List[String]]).getOrElse(allSources)
      case Failure(_) => allSources
    }

    logger.debug(s"Sources to load: ${sources.mkString("\n")}")

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val scheduler = system.scheduler

    val attempt = () => {
      logger.debug("Waiting for server to come live...")
      Http().singleRequest(HttpRequest(uri = serverUrl + "/alive"))
    }
    akka.pattern
      .retry(attempt, 10, 1.seconds)
      .onComplete {
        case Success(res) =>
          Unmarshal(res.entity).to[String].onComplete {
            case Success(body) =>
              if (body != uuid) {
                logger.error(s"uuid not matching - it seems that an instance of scalavista server is already running on port $port - try a different port.")
                serverProcess.destroy()
                System.exit(0)
              }
              logger.debug(s"Scalavista server is live with uuid $uuid")
              val filenames = JsArray(sources.map(sf => JsString(sf)).toVector)
              val fileContents = JsArray(
                sources.map(sf => JsString(File(sf).contentAsString)).toVector)
              val data =
                JsObject("filenames" -> filenames, "fileContents" -> fileContents)
              Http()
                .singleRequest(
                  HttpRequest(
                    method = HttpMethods.POST,
                    uri = serverUrl + "/reload-files",
                    entity =
                      HttpEntity(ContentTypes.`application/json`, data.compactPrint)
                  ))
                .onComplete {
                  case Success(res) =>
                    logger.debug("Successfully loaded source files.")
                    logger.info(s"Scalavista server up and running at $serverUrl.")
                  case Failure(_) => logger.warn("Failed to load source files.")
                }
            case Failure(_) =>
              logger.error("Failed to start server - quitting.")
              serverProcess.destroy()
              System.exit(0)
          }
        case Failure(_) =>
          logger.error("Failed to start server - quitting.")
          serverProcess.destroy()
          System.exit(0)
      }

    logger.info("Press any key to shut down.")
    scala.io.StdIn.readLine()
    serverProcess.destroy()
    System.exit(0)

  }
}
