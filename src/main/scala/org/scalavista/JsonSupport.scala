package org.scalavista

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val typeAtRequestFormat = jsonFormat3(TypeAtRequest)
  implicit val kindAtRequestFormat = jsonFormat3(KindAtRequest)
  implicit val fullyQualifiedNameAtRequestFormat = jsonFormat3(FullyQualifiedNameAtRequest)
  implicit val posAtRequestFormat = jsonFormat3(PosAtRequest)
  implicit val docAtRequestFormat = jsonFormat3(DocAtRequest)
  implicit val reloadFileRequestFormat = jsonFormat2(ReloadFileRequest)
  implicit val reloadFilesRequestFormat = jsonFormat2(ReloadFilesRequest)
  implicit val typeCompletionRequestFormat = jsonFormat3(TypeCompletionRequest)
  implicit val scopeCompletionRequestFormat = jsonFormat3(ScopeCompletionRequest)
}
