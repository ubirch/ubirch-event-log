package com.ubirch.models

trait CustomEncodings[T] {
  _: TablePointer[T] ⇒

  import org.json4s.JsonAST.JValue
  import org.json4s.native.JsonMethods._

  import db._

  implicit def encodeJValue = MappedEncoding[JValue, String](Option(_).map(_.toString).getOrElse(""))
  implicit def decodeJValue = MappedEncoding[String, JValue](x ⇒ parse(x))

}
