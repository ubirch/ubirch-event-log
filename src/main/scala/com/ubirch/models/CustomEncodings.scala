package com.ubirch.models

import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._

trait CustomEncodingsBase {

  import io.getquill.MappedEncoding

  implicit def encodeJValue: MappedEncoding[JValue, String]
  implicit def decodeJValue: MappedEncoding[String, JValue]

}

trait CustomEncodings[T] extends CustomEncodingsBase {
  _: TablePointer[T] =>

  import db._

  implicit def encodeJValue = MappedEncoding[JValue, String](Option(_).map(_.toString).getOrElse(""))
  implicit def decodeJValue = MappedEncoding[String, JValue](x => parse(x))

}
