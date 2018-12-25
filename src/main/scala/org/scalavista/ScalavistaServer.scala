package org.scalavista

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.reflect.internal.util.Position

object ScalavistaServer extends JsonSupport {

  def main(args: Array[String]) {

    val conf = new CliConf(args)

    val port = conf.port()

    val logger = if (conf.trace()) 
        Logger(Logger.Trace) 
      else if (conf.debug()) 
        Logger(Logger.Debug) 
      else 
        Logger(Logger.Info)

    logger.debug(s"port: $port")

    implicit val system = ActorSystem("scalavista-actor-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val scalacOptions = conf.scalacopts.toOption match {
      case Some(opts) => opts.stripPrefix("\"").stripSuffix("\"")
      case _          => ""
    }

    logger.debug(s"scalacOptions: $scalacOptions")

    val engine = ScalavistaEngine(scalacOptions, logger)

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
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("ask-pos-at") {
        post {
          decodeRequest {
            entity(as[PosAtRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val (s, p) = engine.getPosAt(pos)
              val result = Map("symbol" -> s,
                               "file" -> p.source.toString,
                               "line" -> p.line.toString,
                               "column" -> p.column.toString)
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("ask-doc-at") {
        post {
          decodeRequest {
            entity(as[DocAtRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val doc = engine.getDocAt(pos)
              complete((StatusCodes.OK, doc))
            }
          }
        }
      } ~ path("errors") {
        get {
          val errors = engine.getErrors
          complete((StatusCodes.OK, errors))
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
              val result = engine
                .getTypeCompletion(pos)
                .filterNot(_._2.startsWith("[inaccessible]"))
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("scope-completion") {
        post {
          decodeRequest {
            entity(as[ScopeCompletionRequest]) { req =>
              val file = engine.newSourceFile(req.fileContents, req.filename)
              engine.reloadFiles(List(file))
              val pos = Position.offset(file, req.offset)
              val result = engine.getScopeCompletion(pos)
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("log-debug") {
        post {
          decodeRequest {
            entity(as[String]) { req =>
              logger.debug(req)
              complete(StatusCodes.OK)
            }
          }
        }
      }

    Http().bindAndHandle(route, "localhost", port)
    logger.debug(s"scalavista server listening on port $port")
  }
}
