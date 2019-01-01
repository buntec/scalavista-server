package org.scalavista

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
//import akka.stream.ActorMaterializer

import scala.sys.process._
import spray.json._
import DefaultJsonProtocol._ 

import scala.util.{Try, Success, Failure}

import better.files._


object Launcher {

  def main(args: Array[String]) {

    // parse cli options
    val conf = new LauncherCliConf(args)
    val port = conf.port()
    val serverUrl = s"http://localhost:$port"
    val debug = if (conf.debug()) "--debug" else ""
    val trace = if (conf.trace()) "--trace" else ""
    val pedantic = conf.pedantic()

    val logger = if (conf.trace()) 
        Logger(Logger.Trace) 
      else if (conf.debug()) 
        Logger(Logger.Debug) 
      else 
        Logger(Logger.Info)

    // read scalavista.json if present
    val jsonTry = Try{
      val file = file"scalavista.json"
      JsonParser(file.contentAsString).asJsObject
    }

    val preClasspath = System.getProperty("java.class.path")
    val pathSeparator = System.getProperty("file.separator")
    val java = System.getProperty("java.home") + pathSeparator + "bin" + pathSeparator + "java"
    val className = "org.scalavista.ScalavistaServer" 
    val classPathSeparator = 
    if (System.getProperty("os.name").startsWith("Windows")) ";" else ":"

    def combineScalacOptions(options: Seq[String]): String = {
      options.mkString("\"", "&#", "\"")
    }

    // here we branch on whether or not we have a scalavista.json
    val cmd = jsonTry match {

      case Success(json) => 

        val classpath = (preClasspath :: json.fields("classpath")
          .convertTo[List[String]]).mkString(classPathSeparator)

        val scalacOptions = combineScalacOptions(json.fields("scalacOptions").convertTo[List[String]])
        s"$java -cp $classpath $className $debug $trace --port $port --scalacopts $scalacOptions" 
        
      case Failure(_) => 
        
        val jarFolder = file"./jars"

        val jars = if (jarFolder.isDirectory) file"./jars".list
          .filter(f => f.extension == Some(".scala")).map(f => f.pathAsString).toList
          else
            List()

        val classpath = (preClasspath :: jars).mkString(classPathSeparator)

        val scalacOptions = combineScalacOptions(
          if (pedantic) 
            CompilerOptions.pedantic 
          else 
            CompilerOptions.default
        )

        s"$java -cp $classpath $className $debug $trace --port $port --scalacopts $scalacOptions" 

    }

    // spawn the server process
    logger.debug(cmd)
    val serverProcess = Process(cmd).run

    // we want to load all known Scala source files
    val sources = jsonTry match {
      case Success(json) => 
        json.fields("sources").convertTo[List[String]]
      case Failure(_) => 
        file".".listRecursively.filter(_.extension == Some(".scala")).map(_.pathAsString).toList
    }

    logger.debug(s"sources to load: ${sources.mkString("\n")}")

    implicit val system = ActorSystem()
    // implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val scheduler = system.scheduler


    val attempt = () => {
      logger.debug("Waiting for server to come live...")
      Http().singleRequest(HttpRequest(uri = serverUrl + "/alive"))}
    akka.pattern.retry(attempt, 10, 1.seconds)
      .onComplete {
        case Success(res) => 
          logger.debug("Scalavista server is live")
          val filenames = JsArray(sources.map(sf => JsString(sf)).toVector)
          val fileContents = JsArray(sources.map(sf => JsString(File(sf).contentAsString)).toVector)
          val data = JsObject("filenames" -> filenames, "fileContents" -> fileContents)
          Http().singleRequest(HttpRequest(
            method = HttpMethods.POST,
            uri = serverUrl + "/reload-files",
            entity = HttpEntity(ContentTypes.`application/json`, data.compactPrint)
          )).onComplete {

            case Success(res) => 
              logger.debug("Successfully loaded source files.")
              logger.info("Scalavista server up and running.")
            case Failure(_) => logger.info("Failed to load source files.")

          }

        case Failure(_)   => 
          logger.info("Failed to start server - quitting.")
          serverProcess.destroy()
          System.exit(0)
      }

    logger.info("Press any key to shut down.")
    scala.io.StdIn.readLine()
    serverProcess.destroy()
    System.exit(0)

  }
}
