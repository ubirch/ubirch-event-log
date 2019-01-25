package com.ubirch.sdk

import com.typesafe.config.Config
import com.ubirch.models.EventLog
import com.ubirch.sdk.process._
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.InjectorHelper
import org.json4s.JValue

import scala.beans.BeanProperty
import scala.language.implicitConversions

class EventLogging extends InjectorHelper {

  @BeanProperty var stringProducer: StringProducer = get[StringProducer]
  @BeanProperty var config: Config = get[Config]

  private val jValueBuilder = {
    new CreateEventFromJValue(_: String, _: String) andThen
      new PackIntoEventLog
  }

  private def tBuilder[T: Manifest](serviceClass: String, category: String) = {
    new CreateEventFrom[T](serviceClass, category) andThen
      new PackIntoEventLog
  }

  private def committer =
    new Commit(getStringProducer, getConfig) andThen new Logger

  def log(message: JValue) = {
    jValueBuilder(getClass.getName, "")(message)
  }

  def log[T: Manifest](message: T) = {
    tBuilder(getClass.getName, "").apply(message)
  }

  case class EnrichedEventLog(message: EventLog) {

    def commit = committer(message)

    def +>(otherMessage: EventLog) = List(message, otherMessage)

    def <+(otherMessage: EventLog) = this +> otherMessage

  }

  case class EnrichedEventLogs(messages: List[EventLog]) {

    def commit = messages.map(committer)

  }

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)
  implicit def enrichedEventLogs(eventLog: List[EventLog]): EnrichedEventLogs = EnrichedEventLogs(eventLog)

  /*
  def log(message: JValue, category: String): EventLogger[EventLog] =
    LoggerFromJValue(getClass.getName, category)(producer, config).map(x => x(message))

  def log(message: JValue, category: String) =
    LoggerFromJValue(getClass.getName, category).commit

  def log(message: JValue, serviceClass: String, category: String) =
    LoggerFromJValue(serviceClass, category).commit(message)

  def log[T: Manifest](message: T, category: String) =
    LoggerFrom[T](getClass.getName, category)

  def log[T: Manifest](message: T) = Logger(getClass.getName, "").commit(message)

  def log[T: Manifest](message: T, serviceClass: String, category: String) = Logger(serviceClass, category, message)*/

}
