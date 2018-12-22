package org.scalavista

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol

import com.typesafe.scalalogging.LazyLogging

import ch.qos.logback.classic.{Level,Logger}
import org.slf4j.LoggerFactory

import scala.io.StdIn
import scala.reflect.internal.util.Position

sealed trait Request
case class TypeAtRequest(filename: String, fileContents: String, offset: Int)
    extends Request
case class ReloadFileRequest(filename: String, fileContents: String)
    extends Request
case class ReloadFilesRequest(filenames: List[String],
                              fileContents: List[String])
    extends Request
case class TypeCompletionRequest(filename: String,
                                 fileContents: String,
                                 offset: Int)
    extends Request

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val typeAtRequestFormat = jsonFormat3(TypeAtRequest)
  implicit val reloadFileRequestFormat = jsonFormat2(ReloadFileRequest)
  implicit val reloadFilesRequestFormat = jsonFormat2(ReloadFilesRequest)
  implicit val typeCompletionRequestFormat = jsonFormat3(TypeCompletionRequest)
}

object ScalavistaServer extends JsonSupport with LazyLogging {

  private val engine = ScalavistaEngine()

  def main(args: Array[String]) {

    val cmdlnlog: Int = args.map( {
        case "-d" => Level.DEBUG_INT
        case "-dd" => Level.TRACE_INT
        case "-q" => Level.WARN_INT
        case "-qq" => Level.ERROR_INT
        case _ => -1
      } ).foldLeft(Level.OFF_INT)(scala.math.min(_,_))

    if (cmdlnlog == -1) {
      // Unknown log level has been passed in, error out
      Console.err.println("Unsupported command line argument passed in, terminating.")
      sys.exit(0)
    }
    // if nothing has been passed on the command line, use INFO
    val newloglevel = if (cmdlnlog == Level.OFF_INT) Level.INFO_INT else cmdlnlog
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).
      asInstanceOf[Logger].setLevel(Level.toLevel(newloglevel))

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      path("reload-file") {
        post {
          decodeRequest {
            entity(as[ReloadFileRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("reload-files") {
        post {
          decodeRequest {
            entity(as[ReloadFilesRequest]) { req =>
              val files = (req.filenames zip req.fileContents).map {
                case (fn, con) => engine.newSourceFile(con, fn)
              }
              engine.reloadFiles(files)
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("ask-type-at") {
        post {
          decodeRequest {
            entity(as[TypeAtRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val result = engine.getTypeAt(pos)
              complete(StatusCodes.OK, result)
            }
          }
        }
      } ~ path("ask-pos-at") {
        post {
          decodeRequest {
            entity(as[TypeAtRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val (s, p) = engine.getPosAt(pos)
              val result = Map("symbol" -> s, "file" -> p.source.toString, "line" -> p.line.toString, "column" -> p.column.toString)
              complete(StatusCodes.OK, result)
            }
          }
        }
      } ~ path("errors") {
        get {
          val errors = engine.getErrors
          complete(StatusCodes.OK, errors)
        }
      } ~ path("alive") {
        get {
          complete(StatusCodes.OK)
        }
      } ~ path("type-completion") {
        post {
          decodeRequest {
            entity(as[TypeCompletionRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val result = engine.getTypeCompletion(pos)
              complete(StatusCodes.OK, result)
            }
          }
        }
      } ~ path("scope-completion") {
        post {
          decodeRequest {
            entity(as[TypeCompletionRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val result = engine.getScopeCompletion(pos)
              complete(StatusCodes.OK, result)
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 9317)
    logger.debug("Akka http server online")
    //val address = bindingFuture.map(_.localAddress).onComplete(a => println(a.map(_.getPort).get))

    //StdIn.readLine() // let it run until user presses return
    //bindingFuture
    //  .flatMap(_.unbind()) // trigger unbinding from the port
    //  .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
