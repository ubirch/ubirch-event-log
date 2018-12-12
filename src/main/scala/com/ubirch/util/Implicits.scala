package com.ubirch.util

import java.util.Properties
import com.typesafe.config.Config
import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable
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

case class EnrichedConfig(config: Config) {

  /**
   * Convert Typesafe config to Java `Properties`.
   */
  def toProperties: Properties = {
    val props = new Properties()
    config.entrySet().asScala.foreach(entry ⇒ props.put(entry.getKey, entry.getValue.unwrapped().toString))
    props
  }

  /**
   * Convert Typesafe config to a Scala map.
   */
  def toPropertyMap: Map[String, AnyRef] = {
    val map = mutable.Map[String, AnyRef]()
    config.entrySet().asScala.foreach(entry ⇒ map.put(entry.getKey, entry.getValue.unwrapped().toString))
    map.toMap
  }
}

object Implicits {

  implicit def enrichedDatetime(dateTime: DateTime) = EnrichedDatetime(dateTime)

  implicit def enrichedConfig(config: Config) = EnrichedConfig(config)

}
