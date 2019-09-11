package org.scalavista

import scala.util.{Try => ScalaTry}

import scala.reflect.internal.util.{Position, _}
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.{
  Global,
  CommentPreservingTypers,
  InteractiveAnalyzer
}
import scala.tools.nsc.reporters.StoreReporter

import scala.tools.nsc.doc
import scala.tools.nsc.doc.base._
//import scala.tools.nsc.doc.base.comment._

object Engine {

  trait Dummy

  def apply(classPath: String, compilerOptions: Seq[String], logger: Logger): Engine = {

    val settings = new Settings()
    settings.processArgumentString(compilerOptions.mkString(" ")) match {
      case (success, unprocessed) =>
        require(success, "failed to parse compiler options")
        require(unprocessed.isEmpty, s"these compiler options were not accepeted: ${unprocessed.mkString(", ")}")
        logger.debug(s"processArguments: success: $success, unprocessed: $unprocessed")
    }

    settings.classpath.value = classPath
    settings.embeddedDefaults[Dummy] // why is this needed?
    //settings.usejavacp.value = true // what does this do exactly?

    val reporter = new StoreReporter()

    new Engine(settings, reporter, logger)

  }

}

class Engine(settings: Settings,
                       reporter: StoreReporter,
                       logger: Logger)
    extends Global(settings, reporter)
    with MemberLookupBase
    with doc.ScaladocGlobalTrait {

  outer =>

  // needed for MemberLookupBase trait - not sure what all of this does
  // override def forScaladoc = true
  val global: this.type = this
  def chooseLink(links: List[LinkTo]): LinkTo = links.head
  def internalLink(sym: Symbol, site: Symbol) = None
  def toString(link: LinkTo) = link.toString
  def warnNoLink = false
  def findExternalLink(sym: Symbol, name: String) = None

  // this is needed for scaladoc parsing - now sure what this does either
  override lazy val analyzer = new {
    val global: outer.type = outer
  } with doc.ScaladocAnalyzer with InteractiveAnalyzer
  with CommentPreservingTypers {
    override def newTyper(
        context: Context): InteractiveTyper with ScaladocTyper =
      new Typer(context) with InteractiveTyper with ScaladocTyper
  }


  private val driveLetter = raw"^[a-zA-Z]:\\".r
  private val isWindows = System.getProperty("os.name").startsWith("Windows")

  def newSourceFileWithPathNormalization(code: String, filepath: String): BatchSourceFile = {
    //val file = AbstractFile.getFile(filepath)
    //new BatchSourceFile(file, code.toArray)
    val normalizedFilepath = if (isWindows) {
      driveLetter.findFirstIn(filepath) match {
          case Some(_) => s"${filepath.head.toUpper}${filepath.tail}"
          case None => filepath
        }
      } else {
        filepath
      }
    newSourceFile(code, normalizedFilepath)
  }

  def reloadFiles(files: Map[String, String]): Unit = {
    val sourceFiles = files.map {
      case (path, content) => newSourceFileWithPathNormalization(content, path)
    }.toList
    reloadFiles(sourceFiles)
  }


  def reloadFiles(files: List[SourceFile]): Unit = {

    val reloadResponse = new Response[Unit]
    logger.debug(s"reloading files: ${files.mkString("\n")}")
    askReload(files, reloadResponse)
    getResult(reloadResponse)
    logger.trace(
      s"unitOfFile after askReload: ${unitOfFile.map(f => f._1.path.toString).mkString("\n")}"
    )

  }

  def getErrors: List[(String, Int, Int, Int, Int, String, String)] = {

    if (!reporter.infos.isEmpty)
      logger.debug(s"getErrors: ${reporter.infos.size} errors/warnings")

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
    logger.debug(Position.formatMessage(pos, "Getting type at position:", false))
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val result = getResult(askTypeAtResponse) match {
      case Some(tree) =>
        logger.debug(s"tree: ${ask(() => tree.toString)}")
        logger.debug(s"raw tree: ${ask(() => showRaw(tree))}")
        tree match {
          case Literal(constant) =>
            ScalaTry(ask(() => constant.tpe.toString)).getOrElse("")
          case _ =>
            ScalaTry(ask(() => tree.symbol.tpe.toLongString)).getOrElse("")
        }
      case None => ""
    }
    logger.debug(s"getTypeAt: $result")
    result
  }

  def getFullyQualifiedNameAt(pos: Position): String = {
    logger.debug(Position.formatMessage(pos, "Getting fully qualified name at position:", false))
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val result = getResult(askTypeAtResponse) match {
      case Some(tree) =>
        tree match {
          case Literal(constant) => ""
          case _ => ScalaTry(ask(() => tree.symbol.fullNameString)).getOrElse("")
        }
      case None => ""
    }
    logger.debug(s"getFullyQualifiedNameAt: $result")
    result
  }

  def getKindAt(pos: Position): String = {
    logger.debug(Position.formatMessage(pos, "Getting kind at position:", false))
    val askTypeAtResponse = new Response[Tree]
    askTypeAt(pos, askTypeAtResponse)
    val result = getResult(askTypeAtResponse) match {
      case Some(tree) =>
        tree match {
          case Literal(constant) => "literal constant"
          case _ => ScalaTry(ask(() => tree.symbol.kindString)).getOrElse("")
        }
      case None => ""
    }
    logger.debug(s"getKindAt: $result")
    result
  }

  def getPosAt(pos: Position): (String, Position) = {
    logger.debug(
      Position.formatMessage(pos, "Getting position of symbol at :", false)
    )
    ScalaTry {
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
      val pls = symbol.paramLists.map(l => l.map(l => l.fullNameString).mkString(", ")).mkString("\n")
      logger.debug(s"sym.paramList: ${pls}")
      logger.debug(s"sym.associatedFile: ${symbol.associatedFile}")
      logger.debug(s"sym.sanitizedKindString: ${symbol.kindString}")
      logger.debug(s"sym.sourceFile: ${symSourceFile}")
      logger.debug(s"sym.sourceFile.path: ${symSourceFile.map(_.path)}")

      val foundPos = symSourceFile
        .map { sf =>
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
        }
        .getOrElse(NoPosition)
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
    logger.debug("Number of type completion items: " + res.length)
    logger.trace("Result of type completion: " + res.mkString("\n"))
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
    logger.debug("Number of scope completion items: " + res.length)
    logger.trace("Result of scope completion: " + res.mkString("\n"))
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

    logger.debug(s"sym.owner.isFreeType: ${symbolOption.map(_.owner.isFreeType)}")

    logger.debug(s"sym.owner.isClass: ${symbolOption.map(_.owner.isClass)}")


    val docOption =
      for (symbol <- symbolOption; sf <- Option(symbol.sourceFile)) yield {
        if (!symbol.owner.isClass && !symbol.owner.isFreeType) {
          ""
        } else {
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
              case Some((expandable @ _, raw, p @ _)) => raw
              case _                                  => ""
            }
            logger.debug(s"$file -> $doc")
            doc
          // case None => ""
          // }
          case None => ""
        }
      }
      }
    docOption.getOrElse("")
  }

  private def getResult[T](res: Response[T]): Option[T] = {
    val TIMEOUT = 10000 // ms
    res.get(TIMEOUT.toLong) match {
      case Some(Left(t)) => Some(t)
      case Some(Right(ex)) =>
        //ex.printStackTrace()
        logger.debug(s"exception caught in getResult: ${ex.toString}")
        None
      case None =>
        None
    }
  }

}
