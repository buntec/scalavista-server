package org.scalavista

import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.reflect.internal.util.{Position, BatchSourceFile}
//import scala.reflect.io.AbstractFile

object ScalavistaServer extends JsonSupport {

  def main(args: Array[String]) {

    val conf = new CliConf(args)

    val port = conf.port()

    val logger =
      if (conf.trace())
        Logger(Logger.Trace)
      else if (conf.debug())
        Logger(Logger.Debug)
      else
        Logger(Logger.Info)

    logger.debug(s"port: $port")

    implicit val system = ActorSystem("scalavista-actor-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val scalacOptions = conf.scalacopts.toOption match {
      case Some(opts) =>
        opts.stripPrefix("w").stripSuffix("w").split("&#").toSeq
      case _ => Seq()
    }

    logger.debug(s"scalacOptions: $scalacOptions")

    val engine = ScalavistaEngine(scalacOptions, logger)

    val isWindows = System.getProperty("os.name").startsWith("Windows")

    def newSourceFile(code: String, filepath: String): BatchSourceFile = {
      //val file = AbstractFile.getFile(filepath)
      //new BatchSourceFile(file, code.toArray)
      val normalizedFilepath = if (isWindows) filepath.toLowerCase else filepath
      engine.newSourceFile(code, normalizedFilepath)
    }

    def reloadFiles(filePaths: List[String], contents: List[String]): Unit = {
      val sourceFiles = (filePaths zip contents).map {
        case (path, content) => newSourceFile(content, path)
      }
      engine.reloadFiles(sourceFiles)
    }

    val route =
      path("reload-file") {
        post {
          decodeRequest {
            entity(as[ReloadFileRequest]) { req =>
              reloadFiles(List(req.filename), List(req.fileContents))
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("reload-files") {
        post {
          decodeRequest {
            entity(as[ReloadFilesRequest]) { req =>
              reloadFiles(req.filenames, req.fileContents)
              complete(StatusCodes.OK)
            }
          }
        }
      } ~ path("ask-type-at") {
        post {
          decodeRequest {
            entity(as[TypeAtRequest]) { req =>
              val file = newSourceFile(req.fileContents, req.filename)
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
              val file = newSourceFile(req.fileContents, req.filename)
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
              val file = newSourceFile(req.fileContents, req.filename)
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
              val file = newSourceFile(req.fileContents, req.filename)
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
              val file = newSourceFile(req.fileContents, req.filename)
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
