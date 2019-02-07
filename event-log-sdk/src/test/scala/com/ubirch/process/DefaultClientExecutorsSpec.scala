package com.ubirch.process

import java.util
import java.util.Date
import java.util.concurrent.{ExecutionException, Future => JavaFuture}

import com.ubirch.TestBase
import com.ubirch.models.EventLog
import com.ubirch.sdk.process._
import com.ubirch.sdk.util.Exceptions.{CommitException, CommitHandlerASyncException, CommitHandlerSyncException}
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{Execution, ExecutionProvider}
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.ToJson
import org.apache.kafka.clients.producer.{Producer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.json4s.JsonAST.JNull
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

case class Hello(name: String)

class DefaultClientExecutorsSpec extends TestBase with MockitoSugar with Execution {

  "CreateEventFrom" must {

    "create Event" in {

      val data = Hello("Hola")

      val createEventFrom = new CreateEventFrom[Hello]("my_service_class", "my_category")

      val createdEvent = createEventFrom(data)

      createdEvent must not be null

    }

    "throw CreateEventFromException" in {

      val data: Hello = null

      val createEventFrom = new CreateEventFrom[Hello]("my_service_class", "my_category")

      val createdEvent = createEventFrom(data)

      createdEvent.event mustBe JNull
    }
  }

  "CreateEventFromJValue" must {
    "create Event" in {
      val data = ToJson[Hello](Hello("Hola")).get

      val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")

      val createdEvent = createEventFrom(data)

      createdEvent must not be null

      createdEvent.event must be theSameInstanceAs data

    }
  }

  "CreateProducerRecord" must {

    val data = ToJson[Hello](Hello("Hola")).get

    val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
    val eventLog = createEventFrom(data)

    lazy val config = new ConfigProvider().get()

    "commit Event" in {

      val createProducerRecord = new CreateProducerRecord(config)

      val (record, eventLogOut) = createProducerRecord(eventLog)

      assert(record.isInstanceOf[ProducerRecord[String, String]])
      assert(record.value() == eventLogOut.toString)
      assert(eventLog == eventLogOut)

    }

  }

  "Commit" must {

    val data = ToJson[Hello](Hello("Hola")).get

    val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
    val eventLog = createEventFrom(data)

    lazy val config = new ConfigProvider().get()

    val createdProducerRecord = new CreateProducerRecord(config).apply(eventLog)

    "commit Event" in {

      lazy val producer = mock[StringProducer]
      when(producer.producer).thenReturn(mock[Producer[String, String]])
      when(producer.producer.send(any[ProducerRecord[String, String]]()))
        .thenReturn(mock[JavaFuture[RecordMetadata]])

      val commit = new Commit(producer)

      val (javaFutureRecordMetadata, eventLogOut) = commit(createdProducerRecord)

      assert(eventLogOut == eventLog)
      assert(javaFutureRecordMetadata != null)

    }

    "throw CommitException" in {

      lazy val producer = mock[StringProducer]

      val commit = new Commit(producer)

      assertThrows[CommitException](commit(createdProducerRecord))

    }

    "throw CommitException when committing" in {

      lazy val producer = mock[StringProducer]

      val commit = new Commit(producer) {
        override def commit(record: ProducerRecord[String, String]): util.concurrent.Future[RecordMetadata] = {
          throw new ExecutionException("Something happened here", new Exception("OOPS"))
        }
      }

      assertThrows[CommitException](commit(createdProducerRecord))

    }

  }

  "CommitHandlerSync" must {

    val data = ToJson[Hello](Hello("Hola")).get

    val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
    val eventLog = createEventFrom(data)
    val javaRecordMetadata = mock[JavaFuture[RecordMetadata]]

    "handle result" in {

      val handler = new CommitHandlerSync {
        override def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata]): RecordMetadata = {
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

      val resp = handler((javaRecordMetadata, eventLog))

      assert(eventLog == resp)

    }

    "throw CommitHandlerSyncException" in {

      val handler = new CommitHandlerSync {
        override def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata]): RecordMetadata = {
          throw new InterruptedException("OOPs")
        }
      }

      def resp = handler((javaRecordMetadata, eventLog))

      assertThrows[CommitHandlerSyncException](resp)

    }

  }

  "CommitHandlerAsync" must {

    val data = ToJson[Hello](Hello("Hola")).get

    val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
    val eventLog = createEventFrom(data)
    val javaRecordMetadata = mock[JavaFuture[RecordMetadata]]

    "handle result" in {

      val handler = new CommitHandlerAsync {
        override def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata], eventLog: EventLog): Future[EventLog] = {
          Future.successful(eventLog)
        }
      }

      val resp = handler((javaRecordMetadata, eventLog))

      assert(await(resp, 2 seconds) == eventLog)

    }

    "throw CommitHandlerASyncException" in {

      val handler = new CommitHandlerAsync {
        override def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata], eventLog: EventLog): Future[EventLog] = {
          Future.successful(throw new InterruptedException("OOPs"))
        }
      }

      def resp = handler((javaRecordMetadata, eventLog))

      assertThrows[CommitHandlerASyncException](resp)

    }

  }

  "Logger" must {
    "do local log" in {

      val data = ToJson[Hello](Hello("Hola")).get

      val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
      val eventLog = createEventFrom(data)

      val logger = new Logger

      def log = logger(eventLog)

      assert(log == eventLog)

    }

  }

  "Future Logger" must {
    "do local log" in {

      implicit val ec: ExecutionContext = new ExecutionProvider().get()

      val data = ToJson[Hello](Hello("Hola")).get

      val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
      val eventLog = createEventFrom(data)

      val logger = new FutureLogger()

      assert(await(logger(Future.successful(eventLog)), 2 seconds) == eventLog)

    }

  }

}
