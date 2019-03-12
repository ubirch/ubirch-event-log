package com.ubirch.util

import org.json4s._
import org.json4s.jackson.Serialization

/**
  * Helper that injects customized serializers
  */
trait CustomSerializers {
  def all: Iterable[Serializer[_]]
}

/**
  * Helper that contains the basic json formats.
  */
trait WithJsonFormats extends CustomSerializers {
  implicit lazy val formats: Formats = Serialization.formats(NoTypeHints) ++
    org.json4s.ext.JavaTypesSerializers.all ++ all
}

/**
  * Util that provide core functions to go back and forth from values T to
  * JValues and its String representations.
  */
trait JsonHelperBase extends WithJsonFormats {

  def to[T: Manifest](v1: T): JValue = Extraction.decompose(v1)

  def toUnderscore[T: Manifest](v1: T): JValue = to(v1).underscoreKeys

  def get[T: Manifest](v1: JValue): T = Extraction.extract[T](v1)

  def getCamelized[T: Manifest](v1: JValue): T = get(v1.camelizeKeys)

  def stringify(v1: JValue, compact: Boolean = true): String =
    if (compact) jackson.compactJson(v1)
    else jackson.prettyJson(v1)

  def getJValue(v1: String): JValue = jackson.parseJson(v1)

}

/**
  * Helper class that packages the basic json functions and allows for a
  * customized iterable of serializers to be plugged in to the json support environment.
  * @param all Represents a customized iterable with serializers
  */
class JsonHelper(val all: Iterable[Serializer[_]]) extends JsonHelperBase {

  /**
    * Class that allows to convert a value T to JValue.
    * It also allows to create its string representation.
    * @param v1 Represents the value to be parsed.
    * @param manifest$T Represents the manifest of T
    * @tparam T Represents the type of the value v1.
    */
  case class ToJson[T: Manifest](v1: T) {

    def get: JValue = to(v1)

    override def toString: String = stringify(get.underscoreKeys)

    def pretty: String = stringify(get.underscoreKeys, compact = false)

  }

  /**
    * Class that allows to convert a JValue to a value of type T.
    * It also allows to create its string representation.
    * @param v1 Represents the JValue to transform into a value of the T.
    * @param manifest$T Represents the manifest of T
    * @tparam T Represents the type of the value v1.
    */
  case class FromJson[T: Manifest](v1: JValue) {

    def get: T = getCamelized(v1)

    override def toString: String = stringify(v1.underscoreKeys)

    def pretty: String = stringify(v1.underscoreKeys, compact = false)

  }

  /**
    * Class that allows to convert a String to a value of type T.
    * @param v1 Represents the json value in string representation.
    * @param manifest$T Represents the manifest of T
    * @tparam T Represents the type of the value v1.
    */
  case class FromString[T: Manifest](v1: String) {

    def get: T = getCamelized(getJValue(v1).camelizeKeys)

  }

}

/**
  * Simple JsonHelper object with no customized serializers
  */
object JsonHelper extends JsonHelper(Nil)

