package com.ubirch.util

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.{ ZoneId, ZonedDateTime }

import org.joda.time.DateTime

object TimeHelper {

  val bigBangFormat = "dd-M-yyyy hh:mm:ss"
  val bigBangTime = "02-03-1986 00:00:00"

  def bigBangAsDate = {
    val sdf = new SimpleDateFormat(bigBangFormat)
    new DateTime(sdf.parse(bigBangTime))
  }

  def now = new DateTime()

  def toIsoDateTime(time: Long) = {
    java.time.Instant.ofEpochMilli(time).toString
  }

  def toUtc(time: String): String = {
    if (!isUtcTime(time)) addUtcTimestamp(time)
    else time
  }

  def toZonedDateTime(time: String) = {
    val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
    ZonedDateTime.parse(time, dateTimeFormatter)
  }

  private val ZULU_TIME_CHAR = "Z"
  private def addUtcTimestamp(time: String) = time + ZULU_TIME_CHAR
  private def isUtcTime(time: String) = time.last.toString == ZULU_TIME_CHAR

}
