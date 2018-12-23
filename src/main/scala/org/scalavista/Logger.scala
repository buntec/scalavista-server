package org.scalavista


class Logger(val logLevel: Logger.LogLevel) {

  import Logger._

  def error(msg: => String): Unit = if (logLevel.level >= Error.level) println(s"Error: $msg")

  def warn(msg: => String): Unit = if (logLevel.level >= Warn.level) println(s"Warn: $msg")

  def info(msg: => String): Unit = if (logLevel.level >= Info.level) println(s"Info: $msg")

  def debug(msg: => String): Unit = if (logLevel.level >= Debug.level) println(s"Debug: $msg")

  def trace(msg: => String): Unit = if (logLevel.level >= Trace.level) println(s"Trace: $msg")

}

object Logger {

  sealed abstract class LogLevel(val level: Int)
  case object Error extends LogLevel(1)
  case object Warn extends LogLevel(2)
  case object Info extends LogLevel(3)
  case object Debug extends LogLevel(4)
  case object Trace extends LogLevel(5)

  def apply(logLevel: LogLevel): Logger = new Logger(logLevel)

}
