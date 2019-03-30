package com.ubirch.sdk

import com.google.inject.Module
import com.typesafe.config.Config
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.sdk.process._
import com.ubirch.services.ServiceBinder
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.util.InjectorHelper
import org.json4s.JValue

import scala.beans.BeanProperty
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

/**
  * Component for implicit conversions
  */

trait WithConversions {
  _: WithCommitters =>

  implicit def ec: ExecutionContext

  case class EnrichedEventLog(message: EventLog) {

    def commit: EventLog = syncCommitter(message)

    def commitAsync: Future[EventLog] = asyncCommitter(message)

    def commitStealthAsync: EventLog = stealthAsyncCommitter(message)

    //Joiners

    def +>(otherMessage: EventLog) = List(message, otherMessage)

    def <+(otherMessage: EventLog): List[EventLog] = this +> otherMessage

    //Joiners

  }

  case class EnrichedEventLogs(messages: List[EventLog]) {

    def commit: List[EventLog] = messages.map(syncCommitter)

    def commitAsync: List[Future[EventLog]] = messages.map(asyncCommitter)

    def commitStealthAsync: List[EventLog] = messages.map(stealthAsyncCommitter)

  }

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)

  implicit def enrichedEventLogs(eventLog: List[EventLog]): EnrichedEventLogs = EnrichedEventLogs(eventLog)

}

/**
  * Provides committer types
  */

trait WithCommitters {
  _: EventLoggingBase =>

  private def committer = {
    new CreateProducerRecord(getConfig) andThen new Commit(getStringProducer)
  }

  def stealthAsyncCommitter: Executor[EventLog, EventLog] = {
    committer andThen new CommitHandlerStealthAsync andThen new Logger
  }

  def syncCommitter: Executor[EventLog, EventLog] = {
    committer andThen new CommitHandlerSync andThen new Logger
  }

  def asyncCommitter: Executor[EventLog, Future[EventLog]] = {
    committer andThen new CommitHandlerAsync andThen new FutureLogger()
  }

}

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
abstract class EventLoggingBase(modules: List[Module]) extends InjectorHelper(modules) with WithCommitters with WithConversions {

  @BeanProperty var stringProducer: StringProducer = get[StringProducer]
  @BeanProperty var config: Config = get[Config]

  implicit val ec: ExecutionContext = get[ExecutionContext]

  //Helpers to build an Event Log
  private val jValueBuilder = {
    new CreateEventFromJValue(_: String, _: String)
  }

  private def tBuilder[T: Manifest](serviceClass: String, category: String) = {
    new CreateEventFrom[T](serviceClass, category)
  }

  //Helpers to build an Event Log

  //Loggers

  def log(message: JValue): EventLog = {
    jValueBuilder(getClass.getName, "")(message)
  }

  def log(message: JValue, category: String): EventLog = {
    jValueBuilder(getClass.getName, category)(message)
  }

  def log(message: JValue, serviceClass: String, category: String): EventLog = {
    jValueBuilder(serviceClass, category)(message)
  }

  def log[T: Manifest](message: T): EventLog = {
    tBuilder(getClass.getName, "").apply(message)
  }

  def log[T: Manifest](message: T, category: String): EventLog = {
    tBuilder(getClass.getName, category).apply(message)
  }

  def log[T: Manifest](message: T, serviceClass: String, category: String): EventLog = {
    tBuilder(serviceClass, category).apply(message)
  }

  //Loggers

}

class EventLogging extends EventLoggingBase(ServiceBinder.modules)
