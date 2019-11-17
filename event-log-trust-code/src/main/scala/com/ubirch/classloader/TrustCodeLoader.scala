package com.ubirch.classloader

import com.ubirch.models.TrustCode
import javax.inject._

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.util.Try

case class TrustCodeLoad(id: String, trustCode: TrustCode, clazz: Class[TrustCode], methods: List[String])

@Singleton
class TrustCodeLoader() {

  private val tb = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()

  def createFile(id: String, name: String, code: String) = {
    WriteFileControl(10000, "", "SMART_CODE", name, id, "scala").secured {
      writer =>
        writer.append(code)
    }
  }

  def materialize(id: String, code: String) = Try {
    val clazz = tb.compile(tb.parse(code))().asInstanceOf[Class[TrustCode]]
    val ctor = clazz.getDeclaredConstructor()
    val instance = ctor.newInstance()
    val declaredMethods = clazz.getDeclaredMethods.toList.map(_.getName).filterNot(_.contains("$"))
    TrustCodeLoad(id, instance, clazz, declaredMethods)
  }

}
