package com.ubirch.models

import org.json4s.JValue

trait GenericResponseBase[T] {
  val success: Boolean
  val message: String
  val data: JValue
}

case class GenericResponse(success: Boolean, message: String, data: JValue) extends GenericResponseBase[JValue]

object GenericResponse {
  def Success(message: String, data: JValue) = GenericResponse(success = true, message, data)
  def Failure(message: String, data: JValue) = GenericResponse(success = false, message, data)

}

