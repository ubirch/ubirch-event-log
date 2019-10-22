package com.ubirch

import com.ubirch.service.ExtServiceBinder
import com.ubirch.service.rest.RestService
import com.ubirch.services.ServiceBinder
import com.ubirch.util.Boot

import scala.language.postfixOps

object Service extends Boot(ServiceBinder.modules ++ ExtServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    logger.info("Starting Rest")

    val restEndpoint = get[RestService]
    restEndpoint.start

  }

}

class T {

  import com.ubirch.models.TrustCode

  import scala.reflect.runtime.universe
  import scala.tools.reflect.ToolBox

  val tb = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()

  val code = """
               |import com.ubirch.models.TrustCode
               |import org.json4s.JsonAST.JString
               |class Car extends TrustCode {
               |
               |  def createCar: Unit = {
               |     put("hola", JString("hola"))
               |  }
               |
               |  def createCar(name: String): Unit = {
               |     println(name)
               |     put("adios", JString("adios"))
               |  }
               |
               |}
               |scala.reflect.classTag[Car].runtimeClass
               |
               |""".stripMargin

  val clazz = tb.compile(tb.parse(code))().asInstanceOf[Class[TrustCode]]
  val ctor = clazz.getDeclaredConstructor()
  clazz.getMethods.map(_.getName)

  val instance = ctor.newInstance()

  clazz.getDeclaredMethod("createCar", classOf[String]).invoke(instance, "HOOOLA")
  clazz.getDeclaredMethod("createCar", classOf[String]).invoke(instance, "holllllalla")

}

object Service2 {

  def main(args: Array[String]): Unit = {

    new T

  }

}
