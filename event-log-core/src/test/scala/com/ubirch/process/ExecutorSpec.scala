package com.ubirch.process

import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{ Future => JavaFuture }

import com.typesafe.config.{ Config, ConfigValueFactory }
import com.ubirch.ConfPaths.CryptoConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka.consumer.{ DefaultConsumerRecordsManager, PipeData }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.DefaultConsumerRecordsManagerCounter
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Exceptions._
import com.ubirch.{ Entities, TestBase }
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ Producer, ProducerRecord, RecordMetadata }
import org.apache.kafka.common.TopicPartition
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

  "DiscoveryExecutor" must {

    "publish data when there are LookupKeys simple" in {

      val publishedData = new AtomicReference[String]("")

      val config = new ConfigProvider {} get ()
      lazy val producer = mock[StringProducer]
      when(producer.getProducerOrCreate).thenReturn(mock[Producer[String, String]])
      when(producer.getProducerOrCreate.send(any[ProducerRecord[String, String]]())).thenReturn(mock[JavaFuture[RecordMetadata]])
      val basicCommit = new BasicCommit(producer) {
        override def send(pr: ProducerRecord[String, String]): Future[Option[RecordMetadata]] = {
          publishedData.set(pr.value)
          Future.successful {
            Option {
              new RecordMetadata(
                new TopicPartition("topic", 1),
                1,
                1,
                new Date().getTime,
                1L,
                1,
                1
              )
            }
          }
        }
      }

      val discovery = new DiscoveryExecutor(basicCommit, config)
      val consumerRecord = mock[ConsumerRecord[String, String]]
      val data = Entities.Events.eventExample().addLookupKeys(
        LookupKey(
          "my name",
          "my category",
          "my key",
          Seq("my value 1 ")
        ).withKeyLabel("my key label")
          .addValueLabelForAll("my value label for all")
      )
      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val futureDiscovery = discovery(Future.successful(pipeData))
      val discovered = await(futureDiscovery, 2 seconds)

      assert(discovered.eventLog.isDefined)
      assert(Option(data) == discovered.eventLog)
      assert(publishedData.get() == EventLogJsonSupport.ToJson(Relation.fromEventLog(data)).toString)

      val expected = """[{"v1":{"id":"my key","label":"my key label","properties":{}},"v2":{"id":"my value 1 ","label":"my value label for all","properties":{}},"edge":{"properties":{"category":"my category","name":"my name"},"label":"my category"}}]"""

      assert(publishedData.get() == expected)

    }

    "publish data when there are LookupKeys multiple values" in {

      val publishedData = new AtomicReference[String]("")

      val config = new ConfigProvider {} get ()
      lazy val producer = mock[StringProducer]
      when(producer.getProducerOrCreate).thenReturn(mock[Producer[String, String]])
      when(producer.getProducerOrCreate.send(any[ProducerRecord[String, String]]())).thenReturn(mock[JavaFuture[RecordMetadata]])
      val basicCommit = new BasicCommit(producer) {
        override def send(pr: ProducerRecord[String, String]): Future[Option[RecordMetadata]] = {
          publishedData.set(pr.value)
          Future.successful {
            Option {
              new RecordMetadata(
                new TopicPartition("topic", 1),
                1,
                1,
                new Date().getTime,
                1L,
                1,
                1
              )
            }
          }
        }
      }

      val discovery = new DiscoveryExecutor(basicCommit, config)
      val consumerRecord = mock[ConsumerRecord[String, String]]
      val data = Entities.Events.eventExample().addLookupKeys(
        LookupKey(
          "my name",
          "my category",
          "my key",
          Seq("my value 1 ", "my value 2")
        ).withKeyLabel("my key label")
          .addValueLabelForAll("my value label for all")
      )
      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val futureDiscovery = discovery(Future.successful(pipeData))
      val discovered = await(futureDiscovery, 2 seconds)

      assert(discovered.eventLog.isDefined)
      assert(Option(data) == discovered.eventLog)
      assert(publishedData.get() == EventLogJsonSupport.ToJson(Relation.fromEventLog(data)).toString)

      val expected = """[{"v1":{"id":"my key","label":"my key label","properties":{}},"v2":{"id":"my value 1 ","label":"my value label for all","properties":{}},"edge":{"properties":{"category":"my category","name":"my name"},"label":"my category"}},{"v1":{"id":"my key","label":"my key label","properties":{}},"v2":{"id":"my value 2","label":"my value label for all","properties":{}},"edge":{"properties":{"category":"my category","name":"my name"},"label":"my category"}}]"""

      assert(publishedData.get() == expected)

    }

    "return same value when empty lookup keys" in {

      val publishedData = new AtomicReference[String]("[]")

      val config = new ConfigProvider {} get ()
      lazy val producer = mock[StringProducer]
      when(producer.getProducerOrCreate).thenReturn(mock[Producer[String, String]])
      when(producer.getProducerOrCreate.send(any[ProducerRecord[String, String]]())).thenReturn(mock[JavaFuture[RecordMetadata]])
      val basicCommit = new BasicCommit(producer) {
        override def send(pr: ProducerRecord[String, String]): Future[Option[RecordMetadata]] = {
          publishedData.set(pr.value)
          Future.successful {
            Option {
              new RecordMetadata(
                new TopicPartition("topic", 1),
                1,
                1,
                new Date().getTime,
                1L,
                1,
                1
              )
            }
          }
        }
      }

      val discovery = new DiscoveryExecutor(basicCommit, config)
      val consumerRecord = mock[ConsumerRecord[String, String]]
      val data = Entities.Events.eventExample()
      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))

      val futureDiscovery = discovery(Future.successful(pipeData))
      val discovered = await(futureDiscovery, 2 seconds)

      assert(discovered.eventLog.isDefined)
      assert(Option(data) == discovered.eventLog)
      assert(publishedData.get() == EventLogJsonSupport.ToJson(Relation.fromEventLog(data)).toString)
      assert(Relation.fromEventLog(data).isEmpty)

    }

    "return same value when empty lookup keys 2" in {

      val publishedData = new AtomicReference[String]("[]")

      val config = new ConfigProvider {} get ()
      lazy val producer = mock[StringProducer]
      when(producer.getProducerOrCreate).thenReturn(mock[Producer[String, String]])
      when(producer.getProducerOrCreate.send(any[ProducerRecord[String, String]]())).thenReturn(mock[JavaFuture[RecordMetadata]])
      val basicCommit = new BasicCommit(producer) {
        override def send(pr: ProducerRecord[String, String]): Future[Option[RecordMetadata]] = {
          publishedData.set(pr.value)
          Future.successful(None)
        }
      }

      val discovery = new DiscoveryExecutor(basicCommit, config)
      val consumerRecord = mock[ConsumerRecord[String, String]]
      val data = Entities.Events.eventExample().addLookupKeys(LookupKey("my name", "my category", "my key", Seq("my value")))
      when(consumerRecord.value()).thenReturn(data.toJson)

      val pipeData = PipeData(consumerRecord, Some(data))
      val futureDiscovery = discovery(Future.successful(pipeData))
      assertThrows[DiscoveryException](await(futureDiscovery, 2 seconds))

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
        new FilterEmpty(),
        new EventLogParser(),
        new EventLogSigner(config),
        new EventsStore(events),
        new DiscoveryExecutor(basicCommit, config),
        new MetricsLogger(metricsLoggerBasic)
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
