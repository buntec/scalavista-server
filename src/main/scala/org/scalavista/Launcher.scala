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

import better.files._

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

    // read scalavista.json if present
    val jsonTry = Try {
      val file = file"scalavista.json"
      JsonParser(file.contentAsString).asJsObject
    }

    val preClasspath = System.getProperty("java.class.path")
    val pathSeparator = System.getProperty("file.separator")
    val java = System.getProperty("java.home") + pathSeparator + "bin" + pathSeparator + "java"
    val className = "org.scalavista.Server"
    val classPathSeparator =
      if (System.getProperty("os.name").startsWith("Windows")) ";" else ":"

    def combineScalacOptions(options: Seq[String]): String = {
      options.mkString("w", "&#", "w")
    }

    // here we branch on whether or not we have a scalavista.json
    val cmd = jsonTry match {

      case Success(json) =>
        val classpath = (preClasspath :: json
          .fields("classpath")
          .convertTo[List[String]]).mkString(classPathSeparator)

        val scalacOptions = combineScalacOptions(
          json.fields("scalacOptions").convertTo[List[String]])

        logger.debug("succesfully loaded scalavista.json")

        s"$java -cp $classpath $className $debug $trace --uuid $uuid --port $port --scalacopts $scalacOptions"

      case Failure(_) =>
        logger.info("Could not find scalavista.json - proceeding without.")
        val libFolder = file"./lib"

        val jars =
          if (libFolder.isDirectory)
            libFolder.list
              .filter(f => f.extension == Some(".jar"))
              .map(f => f.pathAsString)
              .toList
          else
            List()

        val classpath = (preClasspath :: jars).mkString(classPathSeparator)

        val scalacOptions = combineScalacOptions(
          if (pedantic)
            CompilerOptions.pedantic
          else
            CompilerOptions.default
        )

        s"$java -cp $classpath $className $debug $trace --uuid $uuid --port $port --scalacopts $scalacOptions"

    }

    // spawn the server process
    logger.debug(cmd)
    logger.info("Launching server...")
    val serverProcess = Process(cmd).run

    // we want to load all known Scala source files
    val sources = jsonTry match {
      case Success(json) =>
        json.fields("sources").convertTo[List[String]]
      case Failure(_) =>
        file".".listRecursively
          .filter(f => f.extension == Some(".scala") || f.extension == Some(".java"))
          .map(_.pathAsString)
          .toList
    }

    logger.debug(s"sources to load: ${sources.mkString("\n")}")

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
                println(s"uuid not matching - it seems that an instance of scalavista server is already running on port $port - try a different port.")
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
              println("failed to start server - quitting.")
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
