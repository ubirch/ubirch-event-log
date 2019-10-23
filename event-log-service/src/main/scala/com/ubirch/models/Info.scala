package com.ubirch.models

case class InfoGenericResponse(success: Boolean, message: String, data: Info) extends GenericResponseBase[Info]

case class EventLogGenericResponse(success: Boolean, message: String, data: List[EventLog]) extends GenericResponseBase[List[EventLog]]

case class Info(name: String, description: String, version: String)

case class QueryByCatAndTimeElems(category: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) {
  def validate: Boolean = {
    category.nonEmpty &&
      year >= 1986 &&
      month > 0 &&
      month < 13 &&
      day > 0 &&
      day < 32
  }
}

