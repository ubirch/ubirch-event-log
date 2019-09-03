package com.ubirch.process

import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.CryptoConfPaths
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionImpl
import com.ubirch.services.kafka.consumer.{ DefaultConsumerRecordsManager, PipeData }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ DefaultConsumerRecordsManagerCounter, DefaultMetricsLoggerCounter }
import com.ubirch.util.Exceptions._
import com.ubirch.{ Entities, TestBase }
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{ RecordHeader, RecordHeaders }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class ExecutorSpec extends TestBase with MockitoSugar with ExecutionImpl {

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
      val dataAsString = data.toJson

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(dataAsString)

      val filter = new FilterEmpty

      val filtered = filter(consumerRecord)

      assert(await(filtered, 2 seconds).consumerRecords.headOption.map(_.value()) == Option(dataAsString))

    }

    "throw EmptyValueException when empty value found" in {

      val data = ""

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val filter = new FilterEmpty

      assertThrows[EmptyValueException](await(filter(consumerRecord), 2 seconds))

    }
  }

  "EventLogParser" must {

    "parse successfully" in {

      val data = Entities.Events.eventExample().addTraceHeader(Values.EVENT_LOG_SYSTEM)
      val dataAsString = data.toJson

      val consumerRecord = mock[ConsumerRecord[String, String]]
      when(consumerRecord.value()).thenReturn(dataAsString)

      val pipeData = PipeData(consumerRecord, None)

      val eventLogParser = new EventLogParser

      val parsed = await(eventLogParser(Future.successful(pipeData)), 2 seconds)

      assert(parsed == pipeData.copy(eventLog = Some(data)))

    }

    "throw ParsingIntoEventLogException when empty value found" in {

      val data = ""

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val pipeData = PipeData(consumerRecord, None)

      val eventLogParser = new EventLogParser

      val parsed = eventLogParser(Future.successful(pipeData))
      assertThrows[ParsingIntoEventLogException](await(parsed, 2 seconds))

    }

    "throw ParsingIntoEventLogException when wrong json found" in {

      val data = "{}"

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data)

      val pipeData = PipeData(consumerRecord, None)

      val eventLogParser = new EventLogParser

      val parsed = eventLogParser(Future.successful(pipeData))

      assertThrows[ParsingIntoEventLogException](await(parsed, 2 seconds))

    }

  }

  "EventsStore" must {

    "store successfully" in {

      val data = Entities.Events.eventExample()

      val events = mock[EventsDAO]
      val promiseTest = Promise[Int]()

      when(events.insert(any[EventLogRow](), any[Seq[LookupKeyRow]]())).thenReturn {
        promiseTest.completeWith(Future(1))
        promiseTest.future
      }

      when(events.insertFromEventLog(any[EventLog]())).thenReturn {
        promiseTest.completeWith(Future(1))
        promiseTest.future
      }

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val eventsStore = new EventsStore(events)

      await(eventsStore(Future.successful(pipeData)), 2 seconds)

      await(promiseTest.future, 10 seconds)

      assert(promiseTest.isCompleted)

    }

    "throw StoringIntoEventLogException 1" in {

      val data = Entities.Events.eventExample()

      val events = mock[EventsDAO]
      val promiseTest = Promise[Int]()

      when(events.insert(any[EventLogRow](), any[Seq[LookupKeyRow]]())).thenReturn {
        promiseTest.completeWith(Future(1))
        promiseTest.future
      }

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = Future.successful(PipeData(consumerRecord, None))

      val eventsStore = new EventsStore(events)

      assertThrows[StoringIntoEventLogException](await(eventsStore(pipeData), 10 seconds))

    }

    "throw StoringIntoEventLogException 2" in {

      val data = Entities.Events.eventExample()

      val events = mock[EventsDAO]
      val promiseTest = Promise[Int]()

      when(events.insert(any[EventLogRow](), any[Seq[LookupKeyRow]]())).thenReturn {
        promiseTest.completeWith(Future.failed(new Exception("Something happened when storing")))
        promiseTest.future
      }

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = Future.successful(PipeData(consumerRecord, None))

      val eventsStore = new EventsStore(events)

      assertThrows[StoringIntoEventLogException](await(eventsStore(pipeData), 10 seconds))

    }

  }

  "EventLogSigner" must {

    "sign eventLog" in {

      val config = new ConfigProvider {} get ()
      val signer = new EventLogSigner(config)

      val consumerRecord = mock[ConsumerRecord[String, String]]

      val data = Entities.Events.eventExample()

      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val futureSigned = signer(Future.successful(pipeData))

      val signed = await(futureSigned, 2 seconds)

      assert(signed.eventLog.isDefined)
      assert(Option(data.sign(config)) == signed.eventLog)

    }

    "throw valid exception when invalid key" in {

      object CrytoPath extends CryptoConfPaths

      val config = new ConfigProvider {} get () withValue (CrytoPath.SERVICE_PK, ConfigValueFactory.fromAnyRef(""))
      val signer = new EventLogSigner(config)

      val consumerRecord = mock[ConsumerRecord[String, String]]

      val data = Entities.Events.eventExample()

      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val futureSigned = signer(Future.successful(pipeData))

      assertThrows[SigningEventLogException](await(futureSigned, 2 seconds))

    }

  }

  "Composed DefaultExecutor" must {
    "filter successfully" in {

      val reporter = mock[Reporter]

      val events = mock[EventsDAO]
      val promiseTest = Promise[Int]()

      when(events.insert(any[EventLogRow](), any[Seq[LookupKeyRow]]())).thenReturn {
        promiseTest.completeWith(Future(1))
        promiseTest.future
      }

      val config = mock[Config]
      val basicCommit = mock[BasicCommit]

      val metricsLoggerBasic = mock[MetricsLoggerBasic]

      val family = DefaultExecutorFamily(
        new LoggerExecutor(events, new DefaultMetricsLoggerCounter(config))
      )

      val defaultExecutor = new DefaultConsumerRecordsManager(reporter, family, new DefaultConsumerRecordsManagerCounter(config))

      val consumerRecord = mock[ConsumerRecord[String, String]]

      when(consumerRecord.value()).thenReturn(
        Entities.Events.eventExample().toJson
      )

      val header = new RecordHeader("HolaHeader", "HolaHeaderData".getBytes)

      val headers = new RecordHeaders().add(header)

      when(consumerRecord.headers()).thenReturn(headers)

      val executor = defaultExecutor.executor
      executor(Vector(consumerRecord))

      await(promiseTest.future, 10 seconds)

      assert(promiseTest.isCompleted)

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
