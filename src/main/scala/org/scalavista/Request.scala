package org.scalavista

sealed trait Request

case class TypeAtRequest(filename: String, fileContents: String, offset: Int)
    extends Request

case class KindAtRequest(filename: String, fileContents: String, offset: Int)
    extends Request

case class FullyQualifiedNameAtRequest(filename: String, fileContents: String, offset: Int)
    extends Request

case class PosAtRequest(filename: String, fileContents: String, offset: Int)
    extends Request

case class DocAtRequest(filename: String, fileContents: String, offset: Int)
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

case class ScopeCompletionRequest(filename: String,
                                  fileContents: String,
                                  offset: Int)
    extends Request
