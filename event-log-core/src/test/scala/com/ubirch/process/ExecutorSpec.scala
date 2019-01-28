package com.ubirch.process

import com.ubirch.models.{ EventLog, Events }
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Exceptions.{ EmptyValueException, ParsingIntoEventLogException, StoringIntoEventLogException }
import com.ubirch.{ Entities, TestBase }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{ RecordHeader, RecordHeaders }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class ExecutorSpec extends TestBase with MockitoSugar with Execution {

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

  "FilterEmpty" must {
    "filter successfully" in {

      val data = Entities.Events.eventExample()
      val dataAsString = data.toString

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(dataAsString)

      val filter = new FilterEmpty

      val filtered = filter(consumerRecord)

      assert(filtered.value() == dataAsString)

    }

    "throw EmptyValueException when empty value found" in {

      val data = ""

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val filter = new FilterEmpty

      assertThrows[EmptyValueException](filter(consumerRecord))

    }
  }

  "EventLogParser" must {
    "parse successfully" in {

      val data = Entities.Events.eventExample()
      val dataAsString = data.toString

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(dataAsString)
      val eventLogParser = new EventLogParser

      val parsed = eventLogParser(consumerRecord)

      assert(parsed == data)

    }

    "throw ParsingIntoEventLogException when empty value found" in {

      val data = ""

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val eventLogParser = new EventLogParser

      assertThrows[ParsingIntoEventLogException](eventLogParser(consumerRecord))

    }

    "throw ParsingIntoEventLogException when wrong json found" in {

      val data = "{}"

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val eventLogParser = new EventLogParser

      assertThrows[ParsingIntoEventLogException](eventLogParser(consumerRecord))

    }

  }

  "EventsStore" must {
    "store successfully" in {

      val data = Entities.Events.eventExample()

      val events = mock[Events]
      val promiseTest = Promise[Unit]()

      when(events.insert(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future(()))
        promiseTest.future
      }

      val eventsStore = new EventsStore(events)

      eventsStore(data)

      await(promiseTest.future, 10 seconds)

      assert(promiseTest.isCompleted)

    }

    "throw StoringIntoEventLogException" in {

      val data = Entities.Events.eventExample()

      val events = mock[Events]
      val promiseTest = Promise[Unit]()

      when(events.insert(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future.failed(new Exception("Something happened when storing")))
        promiseTest.future
      }

      val eventsStore = new EventsStore(events)

      assertThrows[StoringIntoEventLogException](await(eventsStore(data), 10 seconds))

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
        new FilterEmpty(),
        new EventLogParser(),
        new EventsStore(events)
      )

      val defaultExecutor = new DefaultExecutor(reporter, family)

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(
        Entities.Events.eventExample().toString
      )

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
