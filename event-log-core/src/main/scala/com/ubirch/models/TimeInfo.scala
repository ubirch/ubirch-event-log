package com.ubirch.models

import java.util.Date

import com.ubirch.util.Implicits.enrichedDate
import io.getquill.Embedded

/**
  * This case class represents the the explicit values of the event time on the cassandra db.
  * They are explicitly shown to help have better clustering keys.
  * This class is an expansion of the event time date.
  * @param year Represents the year when the event took place
  * @param month Represents the month when the event took place
  * @param day Represents the day when the event took place
  * @param hour Represents the hour when the event took place
  * @param minute Represents the minutes when the event took place
  * @param second Represents the seconds when the event took place
  * @param milli Represents the milliseconds when the event took place
  */
case class TimeInfo(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) extends Embedded

object TimeInfo {

  /**
    * Helper to create a TimeInfo easily from a Date value
    * @param date Represents the event date.
    * @return Returns the TimeInfo helper value of the event time.
    */

  def apply(date: Date): TimeInfo = date.buildTimeInfo

}
