package com.ubirch.util

import com.ubirch.models.TimeInfo
import org.joda.time.DateTime
import scala.language.implicitConversions

case class EnrichedDatetime(dateTime: DateTime) {
  def buildTimeInfo = TimeInfo(
    year = dateTime.year().get(),
    month = dateTime.monthOfYear().get(),
    day = dateTime.dayOfMonth().get(),
    hour = dateTime.hourOfDay().get(),
    minute = dateTime.minuteOfHour().get(),
    second = dateTime.secondOfMinute().get(),
    milli = dateTime.millisOfSecond().get())
}

object Implicits {

  implicit def enrichedDatetime(dateTime: DateTime) = EnrichedDatetime(dateTime)

}
