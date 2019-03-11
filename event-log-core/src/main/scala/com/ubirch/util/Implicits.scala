package com.ubirch.util

import java.util.{ Date, Properties }

import com.typesafe.config.Config
import com.ubirch.models.TimeInfo
import org.joda.time._

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable
import scala.language.implicitConversions

/**
  * It is an enriched date
  * @param date Represents the date that gets enriched.
  */
case class EnrichedDate(date: Date) {

  protected def buildDateTime(date: Date): DateTime = new DateTime(date)

  def buildDateTime: DateTime = buildDateTime(date)

  val enrichedDatetime = EnrichedDatetime(buildDateTime)

  def buildTimeInfo: TimeInfo = enrichedDatetime.buildTimeInfo

  def secondsBetween(otherTime: Date): Int = enrichedDatetime.secondsBetween(buildDateTime(otherTime))

}

/**
  * It is an enriched instant
  * @param instant Represents the instant that gets enriched
  */
case class EnrichedInstant(instant: Instant) {

  def millisBetween(other: Instant): Long = new Duration(instant, other).getMillis

}

/**
  * It is an enriched date time.
  * @param dateTime Represents the dateTime that gets enriched.
  */
case class EnrichedDatetime(dateTime: DateTime) {

  def secondsBetween(otherTime: DateTime): Int = Seconds.secondsBetween(dateTime, otherTime).getSeconds

  def buildTimeInfo: TimeInfo = TimeInfo(
    year = dateTime.year().get(),
    month = dateTime.monthOfYear().get(),
    day = dateTime.dayOfMonth().get(),
    hour = dateTime.hourOfDay().get(),
    minute = dateTime.minuteOfHour().get(),
    second = dateTime.secondOfMinute().get(),
    milli = dateTime.millisOfSecond().get()
  )
}

/**
  * It is an enriched configuration.
  * @param config Represents the enriched config
  */
case class EnrichedConfig(config: Config) {

  def asOpt[T](key: String)(f: String => T): Option[T] = {

    if (config.hasPathOrNull(key)) {
      if (config.getIsNull(key)) {
        None
      } else {
        Option(f(key))
      }
    } else {
      None
    }
  }

  def getStringAsOption(key: String): Option[String] = asOpt(key)(config.getString)

  /**
    * Convert Typesafe config to Java `Properties`.
    */
  def toProperties: Properties = {
    val props = new Properties()
    config.entrySet().asScala.foreach(entry => props.put(entry.getKey, entry.getValue.unwrapped().toString))
    props
  }

  /**
    * Convert Typesafe config to a Scala map.
    */
  def toPropertyMap: Map[String, AnyRef] = {
    val map = mutable.Map[String, AnyRef]()
    config.entrySet().asScala.foreach(entry => map.put(entry.getKey, entry.getValue.unwrapped().toString))
    map.toMap
  }
}

/**
  * Util that contains the implicits to create enriched values.
  */
object Implicits {

  implicit def enrichedInstant(instant: Instant): EnrichedInstant = EnrichedInstant(instant)

  implicit def enrichedDate(date: Date): EnrichedDate = EnrichedDate(date)

  implicit def enrichedDatetime(dateTime: DateTime): EnrichedDatetime = EnrichedDatetime(dateTime)

  implicit def enrichedConfig(config: Config): EnrichedConfig = EnrichedConfig(config)

}
