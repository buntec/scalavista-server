package org.scalavista

import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.reflect.internal.util.Position

object Server extends JsonSupport {

  def run(engine: Engine, port: Int, uuid: String, logger: Logger): Unit = {

    implicit val system = ActorSystem("scalavista-actor-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher


    val route =
      path("reload-file") {
        post {
          decodeRequest {
            entity(as[ReloadFileRequest]) { req =>
              engine.reloadFiles(List((req.filename, req.fileContents)).toMap)
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("reload-files") {
        post {
          decodeRequest {
            entity(as[ReloadFilesRequest]) { req =>
              engine.reloadFiles((req.filenames zip req.fileContents).toMap)
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("ask-type-at") {
        post {
          decodeRequest {
            entity(as[TypeAtRequest]) { req =>
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
              val pos = Position.offset(file, req.offset)
              val result = Try(engine.getTypeAt(pos)).getOrElse("")
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("ask-kind-at") {
        post {
          decodeRequest {
            entity(as[KindAtRequest]) { req =>
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
              val pos = Position.offset(file, req.offset)
              val result = Try(engine.getKindAt(pos)).getOrElse("")
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("ask-fully-qualified-name-at") {
        post {
          decodeRequest {
            entity(as[FullyQualifiedNameAtRequest]) { req =>
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
              val pos = Position.offset(file, req.offset)
              val result = Try(engine.getFullyQualifiedNameAt(pos)).getOrElse("")
              complete((StatusCodes.OK, result))
            }
          }
        }
      } ~ path("ask-pos-at") {
        post {
          decodeRequest {
            entity(as[PosAtRequest]) { req =>
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
              val pos = Position.offset(file, req.offset)
              val (s, p) = engine.getPosAt(pos)
              val result = Map("symbol" -> s.toString,
                               "file" -> p.source.file.path.toString,
                               "pos" -> Try(p.point.toString).getOrElse(""),
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
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
              val pos = Position.offset(file, req.offset)
              val doc = Try(engine.getDocAt(pos)).getOrElse("")
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
          complete((StatusCodes.OK, uuid))
        }
      } ~ path("version") {
        get {
          complete((StatusCodes.OK, Version.toString))
        }
      } ~ path("type-completion") {
        post {
          decodeRequest {
            entity(as[TypeCompletionRequest]) { req =>
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
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
              val file = engine.newSourceFileWithPathNormalization(req.fileContents, req.filename)
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
              engine.unitOfFile.foreach(uf => {
                val file = uf._1
                val cu = uf._2
                val content = cu.source.content.mkString
                logger.debug(s"$file: $content")

              })
              complete(StatusCodes.OK)
            }
          }
        }
      }

    Http().bindAndHandle(route, "localhost", port)
    logger.debug(s"scalavista server listening on port $port")
  }
}
