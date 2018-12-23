package org.scalavista

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val typeAtRequestFormat = jsonFormat3(TypeAtRequest)
  implicit val reloadFileRequestFormat = jsonFormat2(ReloadFileRequest)
  implicit val reloadFilesRequestFormat = jsonFormat2(ReloadFilesRequest)
  implicit val typeCompletionRequestFormat = jsonFormat3(TypeCompletionRequest)
}
