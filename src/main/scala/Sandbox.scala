package blah

import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.{Global, Response}
import scala.tools.nsc.reporters.{ConsoleReporter, StoreReporter}
import scala.reflect.internal.util.Position
import scala.reflect.internal.util._
import scala.reflect.runtime.universe.showRaw
import scala.reflect.io.AbstractFile
import scala.reflect.api.Trees

import scala.math.exp

object TestMe {

  val hi = "Hi"

}

object Sandbox {

  trait Foo

  val settings = new Settings()

  settings.embeddedDefaults[Foo]
  settings.usejavacp.value = true

  val reporter = new ConsoleReporter(settings)
  val compiler = new Global(settings, reporter)

  //val file = compiler.newSourceFile(code)
  val file = new BatchSourceFile(AbstractFile.getFile("code"))

  val files = List(file)

  val position = Position.offset(file, 50)
  println(Position.formatMessage(position, "Position:", false))

  def getTypeAt(files: List[SourceFile], position: Position): String = {

    val res = new Response[Unit]
    compiler.askReload(files, res)
    val loadRes = res.get(1000)

    val res2 = new Response[compiler.Tree]
    compiler.askTypeAt(position, res2)
    val treeOption = getResult(res2)

    treeOption match {

      case Some(tree) =>
        tree match {
          case compiler.ValDef(_, _, tpt, _) => compiler.ask(() => tpt.toString)
          case _                             => compiler.ask(() => showRaw(tree) + "\n" + tree.toString)
        }
      case None => "failed to get type...."

    }

  }

  println(getTypeAt(files, position))

  //val res3 = new Response[List[compiler.Member]]
  //compiler.askTypeCompletion(position, res3)
  //printResult(res3)

  //val res4 = new Response[List[compiler.Member]]
  //compiler.askScopeCompletion(position, res4)
  //printResult(res4)

  def getResult[T](res: Response[T]): Option[T] = {
    val TIMEOUT = 10000
    if (!res.isComplete && !res.isCancelled) {
      res.get(TIMEOUT.toLong) match {
        case Some(Left(t)) => Some(t)
        case Some(Right(ex)) =>
          ex.printStackTrace()
          println(ex)
          None
        case None =>
          println("None")
          None
      }
    } else {
      None
    }
  }

}
