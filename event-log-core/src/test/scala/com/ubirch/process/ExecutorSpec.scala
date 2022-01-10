package com.ubirch.process

import com.typesafe.config.Config
import com.ubirch.models._
import com.ubirch.services.execution.ExecutionImpl
import com.ubirch.services.kafka.consumer.DefaultConsumerRecordsManager
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.{ Entities, TestBase }
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{ RecordHeader, RecordHeaders }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.postfixOps

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

      val successCounter = new DefaultSuccessCounter(config)
      val failureCounter = new DefaultFailureCounter(config)

      val family = DefaultExecutorFamily(
        new LoggerExecutor(events, successCounter, failureCounter, config)
      )

      val defaultExecutor = new DefaultConsumerRecordsManager(reporter, family, failureCounter, config)

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
