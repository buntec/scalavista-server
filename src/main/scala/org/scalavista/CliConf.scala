package org.scalavista

import org.rogach.scallop._

/**
  * configuration of command-line options
  */
class CliConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val port = opt[Int](required = true)
  val debug = opt[Boolean]()
  val trace = opt[Boolean]()
  val scalacopts = opt[String]()
  verify()
}
