package com.ubirch.services.kafka.consumer

import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLog
import com.ubirch.process.{ DefaultExecutor, Executor, ExecutorFamily }
import com.ubirch.services.kafka._
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.Exceptions.{ ParsingIntoEventLogException, StoringIntoEventLogException }
import com.ubirch.util.FromString
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.{ Entities, TestBase }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringConsumerSpec" must {

    "run Executors successfully and complete expected promise" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 6000)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toString

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[String]()

        val lifeCycle = mock[DefaultLifecycle]

        val executor = mock[DefaultExecutor]

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[PipeData]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = {

              val promiseTest = Promise[PipeData]()
              promiseTestSuccess.completeWith(Future.successful(v1.value()))

              promiseTest.completeWith(Future.successful(PipeData(v1, Some(entity))))
              promiseTest.future

            }
          }
        }

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          new DefaultConsumerRecordsController(executor)
        )

        consumer.get().startPolling()

        val caseOfInterest = await(promiseTestSuccess.future, 10 seconds)

        assert(caseOfInterest.nonEmpty)
        assert(caseOfInterest == entityAsString)
        assert(FromString[EventLog](caseOfInterest).get == entity)

      }

    }

    "run Executors successfully and complete expected promises when using a different topic" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val topic = NameGiver.giveMeATopicName

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toString

        publishStringMessageToKafka(topic, entityAsString)

        val lifeCycle = mock[DefaultLifecycle]

        val executor = mock[DefaultExecutor]

        val promiseTest = Promise[PipeData]()

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[PipeData]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = {
              promiseTest.completeWith(Future.successful(PipeData(v1, Some(entity))))
              promiseTest.future
            }
          }
        }

        val configs = Configs(
          bootstrapServers = "localhost:" + config.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          new DefaultConsumerRecordsController(executor)

        )

        val cons = consumer.get()

        cons.setTopics(Set(topic))
        cons.setProps(configs)
        cons.startPolling()

        await(promiseTest.future, 10 seconds)

        assert(promiseTest.isCompleted)

      }

    }

    "fail if topic is not provided" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val configs = Configs(
          bootstrapServers = "localhost:" + config.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new StringConsumer()
        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))

        consumer.setProps(configs)
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }
    }

    "fail if no serializers have been set" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val consumer = new StringConsumer()

        consumer.setProps(Map.empty)
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

    "fail if props are empty" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val consumer = new StringConsumer()

        consumer.setProps(Map.empty)
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

    "run Executors successfully and complete expected list of 500 entities" in {

      import scala.concurrent.ExecutionContext.Implicits.global

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val maxEntities = 500
      val listfWithSuccess = scala.collection.mutable.ListBuffer.empty[String]
      val listf = scala.collection.mutable.ListBuffer.empty[Future[PipeData]]
      val max = new AtomicReference[Int](maxEntities)
      val releasePromise = Promise[Boolean]()

      withRunningKafka {

        val topic = NameGiver.giveMeATopicName

        val entities = (1 to maxEntities).map(_ => Entities.Events.eventExample()).toList

        val entitiesAsString = entities.map(_.toString)

        entitiesAsString.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        val lifeCycle = mock[DefaultLifecycle]

        val executor = mock[DefaultExecutor]

        when(executor.executor).thenReturn {
          new Executor[ConsumerRecord[String, String], Future[PipeData]] {
            override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = {

              val promiseTest = Promise[PipeData]()

              val el = Option(FromString[EventLog](v1.value()).get)

              lazy val somethingStored = promiseTest.completeWith(Future.successful(PipeData(v1, el)))

              somethingStored

              listfWithSuccess += v1.value()
              listf += promiseTest.future

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
          bootstrapServers = "localhost:" + config.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          new DefaultConsumerRecordsController(executor)
        )

        val cons = consumer.get()
        cons.setTopics(Set(topic))
        cons.setProps(configs)
        cons.startPolling()

        await(releasePromise.future, 30 seconds)

        val flist = Future.sequence(listf).filter(x => x.nonEmpty)
        val rlist = await(flist, 30 seconds)

        assert(rlist.nonEmpty)
        assert(rlist.count(p => p.eventLog.isDefined) == maxEntities)

        val list = listfWithSuccess.toList

        val entitiesAsStringSize = entitiesAsString.size
        val listSize = list.size

        entitiesAsStringSize must be(listSize)
        entitiesAsString must contain theSameElementsAs list

      }

    }

    "talk to reporter when error occurs" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val topic = NameGiver.giveMeATopicName

      val lifeCycle = mock[DefaultLifecycle]
      val executor = mock[DefaultExecutor]

      val promiseTest = Promise[String]()

      when(executor.executor).thenReturn {
        new Executor[ConsumerRecord[String, String], Future[PipeData]] {
          override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = {
            throw ParsingIntoEventLogException("OH_MY_GOD", PipeData(v1, None))
          }
        }
      }

      val reporter = mock[Reporter]

      when(reporter.Types).thenCallRealMethod()
      import reporter.Types._

      when(reporter.report(any[com.ubirch.models.Error]())).thenReturn {
        promiseTest.completeWith(Future("Received message"))
        mock[Future[RecordMetadata]]
      }

      when(executor.reporter).thenReturn(reporter)

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset =
          OffsetResetStrategy.EARLIEST
      )

      val consumer = new DefaultStringConsumer(
        ConfigFactory.load(),
        lifeCycle,
        new DefaultConsumerRecordsController(executor)
      )

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toString

        publishStringMessageToKafka(topic, entityAsString)

        val cons = consumer.get()
        cons.setTopics(Set(topic))
        cons.setProps(configs)
        cons.startPolling()

        val result = await(promiseTest.future, 10 seconds)

        assert(promiseTest.isCompleted)

        assert(result == "Received message")

      }

    }

    "run an NeedForPauseException and pause and then unpause" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 6000)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toString

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[Unit]()

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        when(reporter.Types).thenCallRealMethod()
        import reporter.Types._

        when(reporter.report(any[com.ubirch.models.Error]())).thenReturn {
          Future.successful(
            new RecordMetadata(
              new TopicPartition("topic", 1),
              1,
              1,
              new Date().getTime,
              1L,
              1,
              1
            )
          )
        }

        val executionFamily = mock[ExecutorFamily]

        val executor = new DefaultExecutor(reporter, executionFamily) {
          override def composed: Executor[ConsumerRecord[String, String], Future[PipeData]] = {
            new Executor[ConsumerRecord[String, String], Future[PipeData]] {
              override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = {

                val promiseTest = Promise[PipeData]()

                val exception = StoringIntoEventLogException("OH OH", PipeData(v1, Some(entity)), "OOPS")

                promiseTestSuccess.completeWith(Future.unit)

                promiseTest.completeWith(Future.failed(exception))
                promiseTest.future

              }
            }
          }
        }

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          new DefaultConsumerRecordsController(executor)
        ).get()

        consumer.startPolling()

        Thread.sleep(5000)

        assert(consumer.isPaused.get())

        Thread.sleep(5000)

        assert(!consumer.isPaused.get())

      }

    }

  }

}
