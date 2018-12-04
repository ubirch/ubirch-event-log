package com.ubirch.models

import io.getquill.Embedded

case class TimeInfo(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) extends Embedded