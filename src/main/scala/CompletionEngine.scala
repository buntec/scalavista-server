package org.scalacompletionserver

import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.{Global, Response}
import scala.tools.nsc.reporters.{Reporter, ConsoleReporter, StoreReporter}
import scala.reflect.internal.util.Position
import scala.reflect.internal.util._
import scala.reflect.runtime.universe.showRaw
import scala.reflect.io.AbstractFile
import scala.reflect.api.Trees

import com.typesafe.scalalogging.LazyLogging


object ScalaCompletionEngine {

  trait Dummy

  def apply(): ScalaCompletionEngine = {

    val settings = new Settings()

    settings.embeddedDefaults[Dummy] // why is this needed?
    settings.usejavacp.value = true // what does this do exactly?

    val reporter = new StoreReporter()

    new ScalaCompletionEngine(settings, reporter)

  }

}

class ScalaCompletionEngine(settings: Settings, reporter: StoreReporter)
    extends Global(settings, reporter) with LazyLogging {


  def reloadFiles(files: List[SourceFile]): Unit = {

    val reloadResponse = new Response[Unit]
    askReload(files, reloadResponse)
    getResult(reloadResponse)

  }

  def getErrors: List[String] = {

    logger.info(reporter.infos.mkString("\n"))
    reporter.infos.map(info => s"${info.pos.line};${info.msg};${info.severity}").toList

  }


  def getTypeAt(pos: Position): String = {

    logger.info(Position.formatMessage(pos, "Getting type at position:", false))
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val result = getResult(askTypeAtResponse) match {
      case Some(tree) =>
        logger.info(s"tree: ${ask(() => tree.toString)}\n raw tree: ${ask(() => showRaw(tree))}")
        if (tree.isType) {
          ask(() => tree.toString)
        } else {
          tree match {
            case ValDef(_, _, tpt, _) => ask(() => tpt.toString)
            case DefDef(_, _, _, _, tpt, _) => ask(() => tpt.toString)
            case ModuleDef(_, name, _) => ask(() => name.toString)
            case _                    => "?"
          }
        }
      case None => "Failed to get type."
    }
    logger.info(s"Result of getTypeAt $result")
    result

  }

  def getTypeCompletion(pos: Position): List[String] = {
    logger.info(Position.formatMessage(pos, "Getting type completion at position:", false))
    val response = new Response[List[Member]]
    askTypeCompletion(pos, response)
    val result = getResult(response) match {
      case Some(ml) =>
        logger.info(s"member list: ${ask(() => ml.mkString("\n"))}\n")
        ml
      case None => Nil
    }
    val res = ask(() => result.map(member => member.infoString))
    println(res.mkString("\n"))
    res

  }

  def getScopeCompletion(pos: Position): List[String] = {
    logger.info(Position.formatMessage(pos, "Getting scope completion at position:", false))
    val response = new Response[List[Member]]
    askScopeCompletion(pos, response)
    val result = getResult(response) match {
      case Some(ml) =>
        //logger.info(s"member list: ${ask(() => ml.mkString("\n"))}\n")
        ml
      case None => Nil
    }
    val res = ask(() => result.map(member => member.infoString))
    println(res.mkString("\n"))
    res

  }


  private def getResult[T](res: Response[T]): Option[T] = {
    val TIMEOUT = 10000 // ms
      res.get(TIMEOUT.toLong) match {
        case Some(Left(t)) => Some(t)
        case Some(Right(ex)) =>
          ex.printStackTrace()
          println(ex)
          None
        case None =>
          None
      }
  }

}
