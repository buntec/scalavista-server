package org.scalacompletionserver

import java.util

import py4j.GatewayServer

import scala.reflect.internal.util.Position
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._


object PythonEntryPoint extends App with LazyLogging {

    private val engine = ScalaCompletionEngine()

    def reloadFiles(fileNames: Array[String], fileContents: Array[String]): Unit = {

      val fs = (fileNames zip fileContents).map{ case (name, cont) => engine.newSourceFile(cont, name)}.toList
      engine.reloadFiles(fs)

    }

    def reloadFile(fileName: String, fileContent: String): Unit = {

      val file = engine.newSourceFile(fileContent, fileName)
      engine.reloadFiles(List(file))

    }

    def getErrors: util.List[String] = {
      engine.getErrors.asJava
    }

    def askTypeAt(fileName: String, fileContent: String, offset: Int): String = {

      val file = engine.newSourceFile(fileContent, fileName)
      engine.reloadFiles(List(file))
      val pos = Position.offset(file, offset)
      engine.getTypeAt(pos)

    }

    def askTypeCompletion(fileName: String, fileContent: String, offset: Int): util.List[String] = {

      val file = engine.newSourceFile(fileContent, fileName)
      engine.reloadFiles(List(file))
      val pos = Position.offset(file, offset)
      val res = engine.getTypeCompletion(pos)
      res.asJava

    }

  def askScopeCompletion(fileName: String, fileContent: String, offset: Int): util.List[String] = {

    val file = engine.newSourceFile(fileContent, fileName)
    engine.reloadFiles(List(file))
    val pos = Position.offset(file, offset)
    val res = engine.getScopeCompletion(pos)
    res.asJava

  }

    val gatewayServer = new GatewayServer(PythonEntryPoint);
    gatewayServer.start();
    logger.info("Scala completion server started...");

}
