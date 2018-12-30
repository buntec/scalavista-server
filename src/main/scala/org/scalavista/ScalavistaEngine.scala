package org.scalavista

import scala.util.{Try => ScalaTry}

import scala.reflect.internal.util.{Position, _}
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.{Global, CommentPreservingTypers, InteractiveAnalyzer}
import scala.tools.nsc.reporters.StoreReporter

import scala.tools.nsc.doc
import scala.tools.nsc.doc.base._
//import scala.tools.nsc.doc.base.comment._

object ScalavistaEngine {

  trait Dummy

  def apply(compilerOptions: String, logger: Logger): ScalavistaEngine = {

    val settings = new Settings()
    settings.processArgumentString(compilerOptions) match {
      case (success, unprocessed) =>
        logger.debug(s"processArguments: $success, $unprocessed")
    }

    settings.embeddedDefaults[Dummy] // why is this needed?
    settings.usejavacp.value = true // what does this do exactly?

    val reporter = new StoreReporter()

    new ScalavistaEngine(settings, reporter, logger)

  }

}

class ScalavistaEngine(settings: Settings,
                       reporter: StoreReporter,
                       logger: Logger)
    extends Global(settings, reporter)
    with MemberLookupBase
    with doc.ScaladocGlobalTrait {

  outer =>

  // needed for MemberLookupBase trait - not sure what all of this does
  override def forScaladoc = true
  val global: this.type = this
  def chooseLink(links: List[LinkTo]): LinkTo = links.head
  def internalLink(sym: Symbol, site: Symbol) = None
  def toString(link: LinkTo) = link.toString
  def warnNoLink = false
  def findExternalLink(sym: Symbol, name: String) = None

  // this is needed for scaladoc parsing - now sure what this does either
  override lazy val analyzer = new {
    val global: outer.type = outer
  } with doc.ScaladocAnalyzer with InteractiveAnalyzer with CommentPreservingTypers {
    override def newTyper(context: Context): InteractiveTyper with ScaladocTyper =
      new Typer(context) with InteractiveTyper with ScaladocTyper
  }

  def reloadFiles(files: List[SourceFile]): Unit = {

    val reloadResponse = new Response[Unit]
    askReload(files, reloadResponse)
    getResult(reloadResponse)
    logger.trace(
      s"unitOfFile after askReload: ${unitOfFile.map(f => f._1.toString).mkString("\n")}"
    )

  }

  def getErrors: List[(String, Int, Int, Int, Int, String, String)] = {

    logger.trace(s"getErrors: ${reporter.infos.mkString("\n")}")

    reporter.infos
      .map(
        info =>
          (
            info.pos.source.path,
            ScalaTry(info.pos.line).getOrElse(0),
            ScalaTry(info.pos.column).getOrElse(0),
            ScalaTry(info.pos.start).getOrElse(0),
            ScalaTry(info.pos.end).getOrElse(0),
            info.msg,
            info.severity.toString
        )
      )
      .toList

  }

  def getTypeAt(pos: Position): String = {

    logger.debug(
      Position.formatMessage(pos, "Getting type at position:", false))
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val result = getResult(askTypeAtResponse) match {
      case Some(tree) =>
        logger.debug(
          s"tree: ${ask(() => tree.toString)}\n raw tree: ${ask(() => showRaw(tree))}"
        )
        tree match { 
          case Literal(constant) => ScalaTry(ask(() => constant.tpe.toString)).getOrElse("")
          case _ => ScalaTry(ask(() => tree.symbol.tpe.toLongString)).getOrElse("")
        }
      case None => ""
    }
    logger.debug(s"getTypeAt: $result")
    result

  }

  def getPosAt(pos: Position): (String, Position) = {
    logger.debug(
      Position.formatMessage(pos, "Getting position of symbol at :", false)
    )
    ScalaTry{
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val symbol = getResult(askTypeAtResponse) match {
      case Some(tree) => ask(() => tree.symbol)
      case None       => throw new RuntimeException("Failed to get position.")
    }
    val symbolFullName = symbol.fullNameString
    logger.debug(s"Looking for definition of ${symbolFullName}")

    logger.debug(s"sym.owner: ${symbol.owner.fullNameString}")

    val symSourceFile = Option(symbol.sourceFile)
    logger.debug(s"sym.sourceFile: $symSourceFile")
    logger.debug(s"sym.sourceFile.path: ${symSourceFile.map(_.path)}")

    val foundPos = symSourceFile.map{sf => 
      unitOfFile.find(_._1.path == sf.path) match {
        case Some((file, compilationUnit)) =>
          logger.debug(s"Looking at file $file")
          val response = new Response[Position]
          askLinkPos(symbol, compilationUnit.source, response)
          val position = getResult(response) match {
            case Some(p) => p
            case _       => NoPosition
          }
          logger.debug(
            s"$file -> ${position.source.toString}, ${position.line}, ${position.column}")
          position
        case None => NoPosition
      }
    }.getOrElse(NoPosition)
    (symbolFullName, if (symbol.pos.isDefined) symbol.pos else foundPos)
    }.getOrElse(("", NoPosition))
  }

  def getTypeCompletion(pos: Position): List[(String, String)] = {
    logger.debug(
      Position.formatMessage(pos, "Getting type completion at position:", false)
    )
    val response = new Response[List[Member]]
    askTypeCompletion(pos, response)
    val result = getResult(response) match {
      case Some(ml) => ml
      case None     => Nil
    }
    val res = ask(
      () => result.map(member => (member.sym.nameString, member.infoString)))
    logger.debug("Result of type completion: " + res.mkString("\n"))
    res

  }

  def getScopeCompletion(pos: Position): List[(String, String)] = {
    logger.debug(
      Position
        .formatMessage(pos, "Getting scope completion at position:", false)
    )
    val response = new Response[List[Member]]
    askScopeCompletion(pos, response)
    val result = getResult(response) match {
      case Some(ml) => ml
      case None     => Nil
    }
    val res = ask(
      () => result.map(member => (member.sym.nameString, member.infoString)))
    logger.debug("Result of scope completion: " + res.mkString("\n"))
    res

  }

  def getDocAt(pos: Position): String = {
    logger.debug(
      Position.formatMessage(pos, "Getting doc of symbol at :", false)
    )
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val symbolOption = Option(getResult(askTypeAtResponse) match {
      case Some(tree) => ask(() => tree.symbol)
      case None       => throw new RuntimeException("Failed to get doc.")
    })
    logger.debug(s"Looking for doc of ${symbolOption.map(_.fullNameString)}")
    logger.debug(s"sym.owner: ${symbolOption.map(_.owner.fullNameString)}")

    val docOption = for (symbol <- symbolOption; sf <- Option(symbol.sourceFile)) yield {
      unitOfFile.find(_._1.path == sf.path) match {
        case Some((file, compilationUnit)) =>
           // val parseResponse = new Response[Tree]
           // askParsedEntered(compilationUnit.source, true, parseResponse)
           // getResult(parseResponse) match {
           // case Some(_) =>
             logger.debug(s"Looking at file $file")
             val docResponse = new Response[(String, String, Position)]
             askDocComment(symbol,
                           compilationUnit.source,
                           symbol.owner,
                           List((symbol, compilationUnit.source)),
                           docResponse)
             val doc = getResult(docResponse) match {
               case Some((expandable @_, raw, p @_)) => raw
               case _                                => ""
             }
             logger.debug(s"$file -> $doc")
             doc
           // case None => ""
           // }
        case None => ""
      }
    }
    docOption.getOrElse("")
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
