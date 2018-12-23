package org.scalavista

sealed trait Request

case class TypeAtRequest(filename: String, fileContents: String, offset: Int) extends Request

case class ReloadFileRequest(filename: String, fileContents: String) extends Request

case class ReloadFilesRequest(filenames: List[String], fileContents: List[String]) extends Request

case class TypeCompletionRequest(filename: String, fileContents: String, offset: Int) extends Request
