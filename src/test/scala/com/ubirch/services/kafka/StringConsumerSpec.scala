package com.ubirch.services.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Events
import com.ubirch.services.lifeCycle.DefaultLifecycle
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Promise }

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringConsumerSpec" must {

    "should respond to simple publish/subscribe test" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {

        publishStringMessageToKafka("test", Entities.Events.eventExampleAsString)

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val p = Promise[Vector[Unit]]()

        val executor = new DefaultExecutor(
          DefaultExecutorFamily(
            new Wrapper,
            new FilterEmpty,
            new EventLogParser,
            new EventsStore(events)),
          events) {

          override def composed: Executor[ConsumerRecords[String, String], Future[Vector[Unit]]] = {
            new Executor[ConsumerRecords[String, String], Future[Vector[Unit]]] {
              override def apply(v1: ConsumerRecords[String, String]): Future[Vector[Unit]] = {
                val f = Future.successful(Vector(()))
                p.completeWith(f)
                f

              }
            }
          }
        }

        val consumer = new DefaultStringConsumerUnit(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get().startPolling()

        assert(await(p.future).nonEmpty)

      }

    }

  }

}