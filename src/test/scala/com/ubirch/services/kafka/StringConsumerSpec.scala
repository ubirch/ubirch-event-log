package com.ubirch.services.kafka

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLog, Events }
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.FromString
import com.ubirch.util.Implicits.configsToProps
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecord, ConsumerRecords, OffsetResetStrategy }
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

        val entity = Entities.Events.eventExample()
        val entityAsString = Entities.Events.eventExampleAsString(entity)

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[String]()

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val executor = mock[DefaultExecutor]

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[Unit]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[Unit] = {

              val promiseTest = Promise[Unit]()
              promiseTestSuccess.completeWith(Future.successful(v1.value()))
              promiseTest.completeWith(Future.successful(()))
              promiseTest.future

            }
          }
        }

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get().startPolling()

        val caseOfInterest = await(promiseTestSuccess.future, 10 seconds)

        assert(caseOfInterest.nonEmpty)
        assert(caseOfInterest == entityAsString)
        assert(FromString[EventLog](caseOfInterest).get == entity)

      }

    }

    "run Executors successfully and complete expected promises when using a different topic" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9093, zooKeeperPort = 6001)

      withRunningKafka {

        val topic = "test2"

        val entity = Entities.Events.eventExample()
        val entityAsString = Entities.Events.eventExampleAsString(entity)

        publishStringMessageToKafka(topic, entityAsString)

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val executor = mock[DefaultExecutor]

        val promiseTest = Promise[Unit]()

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[Unit]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[Unit] = {
              promiseTest.completeWith(Future.successful(()))
              promiseTest.future
            }
          }
        }

        val configs = Configs(
          bootstrapServers = "localhost:9093",
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST)

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get()
          .withTopic(topic)
          .withProps(configs)
          .startPolling()

        val caseOfInterest = await(promiseTest.future, 10 seconds)

        assert(caseOfInterest == ())

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

        val consumer = new StringConsumer("MyThreadName", executor.executor)

        consumer.withProps(configs).startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }
    }

    "fail if props are empty" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9095, zooKeeperPort = 6005)

      withRunningKafka {

        val executor = mock[DefaultExecutor]

        val consumer = new StringConsumer("MyThreadName", executor.executor)

        consumer.withProps(Map.empty).startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

    "run Executors successfully and complete expected list of 500 entities" in {

      import scala.concurrent.ExecutionContext.Implicits.global

      val kafkaPort = 9096
      val zooKeeperPort = 6006

      implicit val config = EmbeddedKafkaConfig(kafkaPort = kafkaPort, zooKeeperPort = zooKeeperPort)

      val maxEntities = 500
      var listfWithSuccess = List.empty[String]
      var listf = List.empty[Future[Unit]]
      val max = new AtomicReference[Int](maxEntities)
      val releasePromise = Promise[Boolean]()

      withRunningKafka {

        val topic = "test2"

        val entities = (0 to maxEntities).map(_ ⇒ Entities.Events.eventExample()).toList

        val entitiesAsString = entities.map(x ⇒ Entities.Events.eventExampleAsString(x))

        entitiesAsString.foreach { entityAsString ⇒
          publishStringMessageToKafka(topic, entityAsString)
        }

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val executor = mock[DefaultExecutor]

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[Unit]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[Unit] = {

              val promiseTest = Promise[Unit]()

              lazy val somethingStored = promiseTest.completeWith(Future.successful(()))

              listfWithSuccess = listfWithSuccess ++ Seq(v1.value())
              somethingStored

              listf = promiseTest.future :: listf

              max.set(max.get() - 1)
              val pending = max.get()
              if (pending == 0) {
                releasePromise.completeWith(Future(true))
              }

              promiseTest.future
            }
          }
        }

        val configs = Configs(
          bootstrapServers = "localhost:" + kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST)

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get()
          .withTopic(topic)
          .withProps(configs)
          .startPolling()

        await(releasePromise.future, 30 seconds)

        val flist = Future.sequence(listf).filter(x ⇒ x.nonEmpty)
        val rlist = await(flist, 30 seconds)

        assert(rlist.nonEmpty)
        assert(rlist.contains(()))

        listfWithSuccess must contain theSameElementsAs entitiesAsString
        listfWithSuccess.size must be(entitiesAsString.size)

      }

    }

  }

}