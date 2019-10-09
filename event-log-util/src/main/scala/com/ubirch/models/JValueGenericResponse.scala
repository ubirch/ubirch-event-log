package com.ubirch.models

import org.json4s.JValue

trait GenericResponseBase[T] {
  val success: Boolean
  val message: String
  val data: T
}

case class JValueGenericResponse(success: Boolean, message: String, data: JValue) extends GenericResponseBase[JValue]

object JValueGenericResponse {
  def Success(message: String, data: JValue) = JValueGenericResponse(success = true, message, data)
  def Failure(message: String, data: JValue) = JValueGenericResponse(success = false, message, data)

}
