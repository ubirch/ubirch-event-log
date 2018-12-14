package com.ubirch.services.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{EventLog, Events}
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.FromString
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringConsumerSpec" must {

    "should run Executors successfully and complete expected promise" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {

        publishStringMessageToKafka("test", Entities.Events.eventExampleAsString)

        val promiseTestSuccess = Promise[Vector[String]]()

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val executor = mock[DefaultExecutor]

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecords[String, String], Future[Vector[Unit]]] {
            override def apply(v1: ConsumerRecords[String, String]): Future[Vector[Unit]] = {

              val promiseTest = Promise[Vector[Unit]]()

              lazy val somethingStored = promiseTest.completeWith(Future.successful(Vector(())))
              lazy val nothingStored = promiseTest.completeWith(Future.successful(Vector.empty[Unit]))

              if (v1.count() > 0) {
                v1.iterator().forEachRemaining { x ⇒
                  if (x.value().nonEmpty) {
                    promiseTestSuccess.completeWith(Future.successful(Vector(x.value())))
                    somethingStored
                  } else {
                    nothingStored
                  }
                }
              } else {
                nothingStored
              }

              promiseTest.future
            }
          }
        }

        val consumer = new DefaultStringConsumerUnit(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get().startPolling()

        val caseOfInterest = await(promiseTestSuccess.future, 10 seconds)

        assert(caseOfInterest.nonEmpty)
        assert(caseOfInterest == Vector(Entities.Events.eventExampleAsString))
        assert(caseOfInterest.size == 1)
        assert(caseOfInterest.map(x ⇒ FromString[EventLog](x).get) == Vector(Entities.Events.eventExample))

      }

    }

  }

}