package com.ubirch.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLog, Events }
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.kafka.{ Entities, MessageEnvelope, TestBase }
import com.ubirch.util.Exceptions.{ EmptyValueException, ParsingIntoEventLogException, StoringIntoEventLogException }
import org.scalatest.mockito.MockitoSugar
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{ RecordHeader, RecordHeaders }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class ExecutorSpec extends TestBase with MockitoSugar with LazyLogging with Execution {

  "Executor Function" must {

    "pass the same result as it comes in" in {

      val executor = new Executor[String, String] {
        override def apply(v1: String): String = v1
      }

      assert(executor("Hello World") == "Hello World")

    }

    "compose two executors" in {

      val executor1 = new Executor[String, String] {
        override def apply(v1: String): String = v1.toLowerCase()
      }

      val executor2 = new Executor[String, String] {
        override def apply(v1: String): String = v1.reverse
      }

      val composed = executor1 andThen executor2

      assert(composed("Hello World") == "dlrow olleh")

    }

  }

  "Wrapper" must {
    "wrap consumer record successfully" in {

      val wrapper = new Wrapper

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn("this is a value")

      val header = new RecordHeader("HolaHeader", "HolaHeaderData".getBytes)

      val headers = new RecordHeaders().add(header)

      when(consumerRecord.headers()).thenReturn(headers)

      val messageEnvelope = wrapper(consumerRecord)

      assert(messageEnvelope.payload == consumerRecord.value())

      assert(messageEnvelope.headers == MessageEnvelope.headersToMap(consumerRecord))

    }
  }

  "FilterEmpty" must {
    "filter successfully" in {

      val messageEnvelope = MessageEnvelope("this is a payload", Map("headerX1" -> "headerX1Data"))

      val filter = new FilterEmpty

      val filtered = filter(messageEnvelope)

      assert(filtered == messageEnvelope)

    }

    "throw EmptyValueException when empty value found" in {

      val messageEnvelope = MessageEnvelope("", Map("headerX1" -> "headerX1Data"))

      val filter = new FilterEmpty

      assertThrows[EmptyValueException](filter(messageEnvelope))

    }
  }

  "EventLogParser" must {
    "parse successfully" in {

      val messageEnvelope = MessageEnvelope(
        Entities.Events.eventExampleAsString(Entities.Events.eventExample()),
        Map("headerX1" -> "headerX1Data"))

      val filter = new FilterEmpty

      val filtered = filter(messageEnvelope)

      assert(filtered == messageEnvelope)

    }

    "throw ParsingIntoEventLogException when empty value found" in {

      val messageEnvelope = MessageEnvelope("", Map("headerX1" -> "headerX1Data"))

      val eventLogParser = new EventLogParser

      assertThrows[ParsingIntoEventLogException](eventLogParser(messageEnvelope))

    }

    "throw ParsingIntoEventLogException when wrong json found" in {

      val messageEnvelope = MessageEnvelope("{}", Map("headerX1" -> "headerX1Data"))

      val eventLogParser = new EventLogParser

      assertThrows[ParsingIntoEventLogException](eventLogParser(messageEnvelope))

    }

  }

  "EventsStore" must {
    "store successfully" in {

      val messageEnvelope = MessageEnvelope(Entities.Events.eventExample(), Map("headerX1" -> "headerX1Data"))

      val events = mock[Events]
      val promiseTest = Promise[Unit]()

      when(events.insert(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future(()))
        promiseTest.future
      }

      val eventsStore = new EventsStore(events)

      eventsStore(messageEnvelope)

      await(promiseTest.future, 10 seconds)

      assert(promiseTest.isCompleted)

    }

    "throw StoringIntoEventLogException" in {

      val messageEnvelope = MessageEnvelope(Entities.Events.eventExample(), Map("headerX1" -> "headerX1Data"))

      val events = mock[Events]
      val promiseTest = Promise[Unit]()

      when(events.insert(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future.failed(new Exception("Something happened when storing")))
        promiseTest.future
      }

      val eventsStore = new EventsStore(events)

      assertThrows[StoringIntoEventLogException](await(eventsStore(messageEnvelope), 10 seconds))

    }

  }

  "Composed DefaultExecutor" must {
    "filter successfully" in {

      val reporter = mock[Reporter]

      val events = mock[Events]
      val promiseTest = Promise[Unit]()

      when(events.insert(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future(()))
        promiseTest.future
      }

      val family = DefaultExecutorFamily(
        new Wrapper(),
        new FilterEmpty(),
        new EventLogParser(),
        new EventsStore(events))

      val defaultExecutor = new DefaultExecutor(reporter, family)

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(
        Entities.Events.eventExampleAsString(Entities.Events.eventExample()))

      val header = new RecordHeader("HolaHeader", "HolaHeaderData".getBytes)

      val headers = new RecordHeaders().add(header)

      when(consumerRecord.headers()).thenReturn(headers)

      val executor = defaultExecutor.executor
      executor(consumerRecord)

      await(promiseTest.future, 10 seconds)

      assert(promiseTest.isCompleted)

    }

  }

}