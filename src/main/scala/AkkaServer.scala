import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.scalacompletionserver.ScalaCompletionEngine
import spray.json.DefaultJsonProtocol

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

object AkkaServer extends JsonSupport {

  private val engine = ScalaCompletionEngine()

  def main(args: Array[String]) {

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
      } ~ path("errors") {
        get {
          val errors = engine.getErrors
          complete(StatusCodes.OK, errors)
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

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Scala completion server running\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
