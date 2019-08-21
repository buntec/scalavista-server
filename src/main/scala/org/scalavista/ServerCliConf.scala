package org.scalavista

import org.rogach.scallop._

/**
  * configuration of command-line options for the server
  */
class ServerCliConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val port = opt[Int](required = true)
  val debug = opt[Boolean]()
  val trace = opt[Boolean]()
  val uuid = opt[String]()
  val scalacopts = opt[String]()
  verify()
}
