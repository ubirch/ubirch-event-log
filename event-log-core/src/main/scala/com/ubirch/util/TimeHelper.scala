package com.ubirch.util

import java.text.SimpleDateFormat

import org.joda.time.DateTime

object TimeHelper {

  val bigBangFormat = "dd-M-yyyy hh:mm:ss"
  val bigBangTime = "02-03-1986 00:00:00"

  def bigBangAsDate = {
    val sdf = new SimpleDateFormat(bigBangFormat)
    new DateTime(sdf.parse(bigBangTime))
  }

}
