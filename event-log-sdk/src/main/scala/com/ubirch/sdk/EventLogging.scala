package com.ubirch.sdk

import com.typesafe.config.Config
import com.ubirch.models.EventLog
import com.ubirch.sdk.process._
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.InjectorHelper
import org.json4s.JValue

import scala.beans.BeanProperty
import scala.language.implicitConversions

/**
  * Represents the convenience that is used to log messages into Kafka.
  *
  * <pre>
  * object SDKExample extends EventLogging {
  *
  * def main(args: Array[String]): Unit = {
  *
  * case class Hello(name: String)
  *
  * //From JValue
  * val log1 = log(ToJson(Hello("Hola")).get, "My Category")
  *
  * val log2 = log(ToJson(Hello("Como estas")).get, "My another Category")
  *
  * //Let's unite them in order first in first out
  * val log1_2 = log1 +> log2
  *
  * //Let's actually commit it
  * log1_2.commit
  *
  * //Another Log From A Case Class
  * val log3 = log(Hello("Hola"), "Category")
  *
  * val log4 = log(Hello("Como estas"))
  *
  * //Let's unite them in order first in last out
  * val log3_4 = log3 <+ log4
  *
  * //Let's actually commit it
  * log3_4.commit
  *
  * //Wanna have list of events and fold it
  *
  * val foldedLogs = List(
  * log(Hello("Hello")),
  * log(Hello("Hallo")),
  * log(Hello("Hola")))
  *
  *     foldedLogs.commit
  *
  * //By default the service class is the class extending or mixing the EventLogging trait
  * //But you can also change it
  *
  * val log5 = log(Hello("Buenos Dias"), "THIS_IS_MY_CUSTOMIZED_SERVICE_CLASS", "Category")
  *
  * log5.commit
  *
  * }
  *
  * }
  * </pre>
  */
class EventLogging extends InjectorHelper {

  @BeanProperty var stringProducer: StringProducer = get[StringProducer]
  @BeanProperty var config: Config = get[Config]

  private val jValueBuilder = {
    new CreateEventFromJValue(_: String, _: String)
  }

  private def tBuilder[T: Manifest](serviceClass: String, category: String) = {
    new CreateEventFrom[T](serviceClass, category)
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
