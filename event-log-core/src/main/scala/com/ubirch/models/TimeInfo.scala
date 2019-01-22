package com.ubirch.models

import java.util.Date

import io.getquill.Embedded
import com.ubirch.util.Implicits.enrichedDate

case class TimeInfo(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) extends Embedded

object TimeInfo {

  def apply(date: Date): TimeInfo = date.buildTimeInfo

}
