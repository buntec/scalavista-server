package org.scalavista
import org.rogach.scallop._

/**
  * command-line options for the launcher
  */
class LauncherCliConf(arguments: Seq[String]) extends ScallopConf(arguments) {
  version(s"scalavista-server ${Version.toString}")
  val port = opt[Int](default = Some(9317),
                      descr = "Which server port to use - defaults to 9317")
  val debug = opt[Boolean](descr = "Switch on debug mode")
  val trace = opt[Boolean](descr = "Switch on trace mode")
  val pedantic =
    opt[Boolean](descr = "Switch on pedantic scalac compiler options")
  verify()
}
