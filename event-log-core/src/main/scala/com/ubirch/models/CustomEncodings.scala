package com.ubirch.models

import com.ubirch.util.FromJson
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods._

/**
  * Represents special conversions or mappings for the cassandra db.
  */
trait CustomEncodingsBase {

  import io.getquill.MappedEncoding

  implicit def encodeJValue: MappedEncoding[JValue, String]
  implicit def decodeJValue: MappedEncoding[String, JValue]

}

/**
  * Implements the mappings for the custom values and links it to a
  * specific value that is the actual representation of the table in
  * cassandra.
  * @tparam T Represent the value that holds the data for the database table.
  */
trait CustomEncodings[T] extends CustomEncodingsBase {
  _: TablePointer[T] =>

  import db._

  implicit def encodeJValue = MappedEncoding[JValue, String](Option(_).map(FromJson(_).toString).getOrElse(""))
  implicit def decodeJValue = MappedEncoding[String, JValue](x => parse(x))

}
