package org.scalacompletionserver

import py4j.GatewayServer

import scala.reflect.internal.util.Position

import com.typesafe.scalalogging.LazyLogging


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

    def getErrors: String = {
      engine.getErrors
    }

    def askTypeAt(fileName: String, fileContent: String, offset: Int): String = {

      val file = engine.newSourceFile(fileContent, fileName)
      engine.reloadFiles(List(file))
      val pos = Position.offset(file, offset)
      engine.getTypeAt(pos)

    }

    val gatewayServer = new GatewayServer(PythonEntryPoint);
    gatewayServer.start();
    logger.info("Scala completion server started...");

}
