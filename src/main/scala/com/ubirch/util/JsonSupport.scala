package com.ubirch.util

import org.json4s._
import org.json4s.jackson.Serialization

trait WithJsonFormats {
  implicit lazy val formats = Serialization.formats(NoTypeHints) ++
    org.json4s.ext.JavaTypesSerializers.all
}

trait JsonHelper extends WithJsonFormats {

  def to[T: Manifest](v1: T): JValue = Extraction.decompose(v1)

  def toUnderscore[T: Manifest](v1: T): JValue = to(v1).underscoreKeys

  def get[T: Manifest](v1: JValue): T = Extraction.extract[T](v1)

  def getCamelized[T: Manifest](v1: JValue): T = get(v1.camelizeKeys)

  def stringify(v1: JValue): String = jackson.prettyJson(v1)

}

case class ToJson[T: Manifest](v1: T) extends JsonHelper {

  def get: JValue = to(v1)

  override def toString: String = stringify(get)

}

case class FromJson[T: Manifest](v1: JValue) extends JsonHelper {

  def get: T = getCamelized(v1)

  override def toString: String = stringify(v1)

}

case class FromString[T: Manifest](v1: String) extends JsonHelper {

  def get: T = getCamelized(jackson.parseJson(v1).camelizeKeys)

}
