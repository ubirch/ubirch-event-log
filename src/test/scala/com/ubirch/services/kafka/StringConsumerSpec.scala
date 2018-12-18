package com.ubirch.services.kafka

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLog, Events }
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.FromString
import com.ubirch.util.Implicits.configsToProps
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecords, OffsetResetStrategy }
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringConsumerSpec" must {

    "run Executors successfully and complete expected promise" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {

        publishStringMessageToKafka("com.ubirch.eventlog", Entities.Events.eventExampleAsString)

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

    "run Executors successfully and complete expected list of promises" in {

      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9093, zooKeeperPort = 6001)

      var listf = List[Future[Vector[Unit]]]()
      val max = new AtomicReference[Int](10)
      val releasePromise = Promise[Boolean]()

      withRunningKafka {

        val topic = "test2"

        publishStringMessageToKafka(topic, Entities.Events.eventExampleAsString)

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
                    somethingStored
                  } else {
                    nothingStored
                  }
                }
              } else {
                nothingStored
              }

              listf = promiseTest.future :: listf

              max.set(max.get() - 1)
              val pending = max.get()
              if (pending == 0) {
                releasePromise.success(true)
              }

              promiseTest.future
            }
          }
        }

        val configs = Configs(
          bootstrapServers = "localhost:9093",
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST)

        val consumer = new DefaultStringConsumerUnit(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get()
          .withTopic(topic)
          .withProps(configs)
          .startPolling()

        await(releasePromise.future, 10 seconds)

        val flist = Future.sequence(listf).filter(x ⇒ x.nonEmpty)
        val rlist = await(flist, 10 seconds)

        assert(rlist.nonEmpty)
        assert(rlist.exists(_.nonEmpty))

      }

    }

    "fail if topic is not provided" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9094, zooKeeperPort = 6004)

      withRunningKafka {

        val executor = mock[DefaultExecutor]

        val configs = Configs(
          bootstrapServers = "localhost:9094",
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST)

        val consumer = new StringConsumer[Vector[Unit]]("MyThreadName", executor.executor)

        consumer.withProps(configs).startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }
    }

    "fail if props are empty" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9095, zooKeeperPort = 6005)

      withRunningKafka {

        val executor = mock[DefaultExecutor]

        val consumer = new StringConsumer[Vector[Unit]]("MyThreadName", executor.executor)

        consumer.withProps(Map.empty).startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

  }

}