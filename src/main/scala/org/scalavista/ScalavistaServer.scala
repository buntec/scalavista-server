package org.scalavista

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import com.typesafe.scalalogging.LazyLogging
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory

import scala.reflect.internal.util.Position



object ScalavistaServer extends JsonSupport with LazyLogging {

  def main(args: Array[String]) {

    val conf = new CliConf(args)

    val port = conf.port()

    val logLevel = if (conf.debug()) Level.DEBUG_INT else Level.INFO_INT

    LoggerFactory
      .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[Logger]
      .setLevel(Level.toLevel(logLevel))

    logger.debug(s"port: $port")

    implicit val system = ActorSystem("scalavista-actor-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val scalacOptions = conf.scalacopts.toOption match {
      case Some(opts) => opts.stripPrefix("\"").stripSuffix("\"")
      case _ => ""
    }

    logger.debug(s"scalacOptions: $scalacOptions")

    val engine = ScalavistaEngine(scalacOptions)

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
              val result = Map("symbol" -> s,
                               "file" -> p.source.toString,
                               "line" -> p.line.toString,
                               "column" -> p.column.toString)
              complete(StatusCodes.OK, result)
            }
          }
        }
      } ~ path("ask-doc-at") {
        post {
          decodeRequest {
            entity(as[TypeAtRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val doc = engine.getDocAt(pos)
              complete(StatusCodes.OK, doc)
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

    Http().bindAndHandle(route, "localhost", port)
    logger.debug(s"scalavista server listening on port $port")
  }
}
