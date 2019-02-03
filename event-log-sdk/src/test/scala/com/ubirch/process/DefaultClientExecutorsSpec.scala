package com.ubirch.process

import com.ubirch.TestBase
import com.ubirch.sdk.process.{ Commit, CreateEventFrom, CreateEventFromJValue, Logger }
import com.ubirch.sdk.util.Exceptions.CommitException
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.ToJson
import org.apache.kafka.clients.producer.{ Producer, ProducerRecord, RecordMetadata }
import org.json4s.JsonAST.JNull
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

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

  "Commit" must {

    val data = ToJson[Hello](Hello("Hola")).get

    val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
    val eventLog = createEventFrom(data)

    lazy val config = new ConfigProvider().get()

    "commit Event" in {

      lazy val producer = mock[StringProducer]
      when(producer.producer).thenReturn(mock[Producer[String, String]])
      when(producer.producer.send(any[ProducerRecord[String, String]]()))
        .thenReturn(mock[java.util.concurrent.Future[RecordMetadata]])

      val commit = new Commit(producer, config)

      assert(commit(eventLog) == eventLog)

    }

    "throw CommitException" in {

      lazy val producer = mock[StringProducer]

      val commit = new Commit(producer, config)

      assertThrows[CommitException](commit(eventLog))

    }
  }

  "Logger" must {
    "do local log" in {

      val data = ToJson[Hello](Hello("Hola")).get

      val createEventFrom = new CreateEventFromJValue("my_service_class", "my_category")
      val eventLog = createEventFrom(data)

      val logger = new Logger

      assert(logger(eventLog) == eventLog)

    }

  }

}
