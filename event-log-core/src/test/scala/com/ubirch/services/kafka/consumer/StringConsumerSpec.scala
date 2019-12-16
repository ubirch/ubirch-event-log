package com.ubirch.services.kafka.consumer

import java.util.Date
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.{ Configs, StringConsumer }
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.models.EventLog
import com.ubirch.process.{ Executor, ExecutorFamily }
import com.ubirch.services.execution.ExecutionImpl
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.services.metrics.DefaultFailureCounter
import com.ubirch.util.Exceptions.{ ParsingIntoEventLogException, StoringIntoEventLogException }
import com.ubirch.util.{ EventLogJsonSupport, NameGiver, PortGiver }
import com.ubirch.{ Entities, TestBase }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.language.{ implicitConversions, postfixOps }

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging with ExecutionImpl {

  val config = ConfigFactory.load()
  val counter = new DefaultFailureCounter(config)

  def spawn(kafkaPort: Int): StringConsumer = {
    val lifeCycle = mock[DefaultLifecycle]

    val reporter = mock[Reporter]
    val family = mock[ExecutorFamily]

    val consumerBuilder = new DefaultStringConsumer(
      config,
      lifeCycle,
      new DefaultConsumerRecordsManager(reporter, family, counter, config)
    ) {
      override def consumerConfigs: ConfigProperties = {
        Configs(
          bootstrapServers = "localhost:" + kafkaPort,
          groupId = "My_Group_ID",
          enableAutoCommit = false,
          autoOffsetReset = OffsetResetStrategy.EARLIEST
        )
      }
    }

    val consumer = consumerBuilder.get()

    consumer.setUseSelfAsRebalanceListener(true)
    consumer.startPolling()

    consumer
  }

  def spawn2: StringConsumer = {

    val lifeCycle = mock[DefaultLifecycle]

    val reporter = mock[Reporter]

    val executionFamily = mock[ExecutorFamily]

    val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
      override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
        new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
          override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {
            val promiseTest = Promise[PipeData]()
            val el = v1.headOption.map(x => EventLogJsonSupport.FromString[EventLog](x.value()).get)

            promiseTest.completeWith(Future.successful(PipeData(v1, el)))
            promiseTest.future

          }
        }
      }
    }

    val consumer = new DefaultStringConsumer(
      ConfigFactory.load(),
      lifeCycle,
      recordsManager
    ).get()

    consumer.setUseSelfAsRebalanceListener(false)

    consumer.startPolling()

    consumer
  }

  "StringConsumerSpec" must {

    "run Executors successfully and complete expected promise" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 6000)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toJson

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[String]()

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {

                val promiseTest = Promise[PipeData]()
                promiseTestSuccess.completeWith(Future.successful(v1.headOption.map(_.value()).getOrElse("")))

                promiseTest.completeWith(Future.successful(PipeData(v1, Some(entity))))
                promiseTest.future

              }
            }
          }
        }

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          recordsManager
        )

        consumer.get().startPolling()

        val caseOfInterest = await(promiseTestSuccess.future, 10 seconds)

        assert(caseOfInterest.nonEmpty)
        assert(caseOfInterest == entityAsString)
        assert(EventLogJsonSupport.FromString[EventLog](caseOfInterest).get == entity)

      }

    }

    "run Executors successfully and complete expected promises when using a different topic" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val topic = NameGiver.giveMeATopicName

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toJson

        publishStringMessageToKafka(topic, entityAsString)

        val lifeCycle = mock[DefaultLifecycle]

        val promiseTest = Promise[PipeData]()

        val reporter = mock[Reporter]

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {
                promiseTest.completeWith(Future.successful(PipeData(v1, Some(entity))))
                promiseTest.future
              }
            }
          }
        }

        val configs = Configs(
          bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          recordsManager
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

      implicit val kakfaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val configs = Configs(
          bootstrapServers = "localhost:" + kakfaConfig.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new StringConsumer() {}
        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setForceExit(false) // We disable the ForceExit so that the Test doesn't exit
        consumer.setProps(configs)
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }
    }

    "fail if no serializers have been set" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val consumer = new StringConsumer() {}

        consumer.setProps(Map.empty)
        consumer.setForceExit(false) // We disable the ForceExit so that the Test doesn't exit
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

    "fail if props are empty" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val consumer = new StringConsumer() {}

        consumer.setProps(Map.empty)
        consumer.setForceExit(false) // We disable the ForceExit so that the Test doesn't exit
        consumer.startPolling()

        Thread.sleep(5000) // We wait here so the change is propagated

        assert(!consumer.getRunning)

      }

    }

    "run Executors successfully and complete expected list of 500 entities" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val maxEntities = 500
      val listfWithSuccess = scala.collection.mutable.ListBuffer.empty[String]
      val listf = scala.collection.mutable.ListBuffer.empty[Future[PipeData]]
      val max = new AtomicReference[Int](maxEntities)
      val releasePromise = Promise[Boolean]()

      withRunningKafka {

        val topic = NameGiver.giveMeATopicName

        val entities = (1 to maxEntities).map(_ => Entities.Events.eventExample()).toList

        val entitiesAsString = entities.map(_.toJson)

        entitiesAsString.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {

                val promiseTest = Promise[PipeData]()

                val el = v1.headOption.map(x => EventLogJsonSupport.FromString[EventLog](x.value()).get)

                lazy val somethingStored = promiseTest.completeWith(Future.successful(PipeData(v1, el)))

                somethingStored

                listfWithSuccess += v1.headOption.map(_.value()).getOrElse("")
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
        }

        val configs = Configs(
          bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          recordsManager
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

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val entityAsString = "{}"

        val recordData = new RecordMetadata(
          new TopicPartition("topic", 1),
          1,
          1,
          new Date().getTime,
          1L,
          1,
          1
        )

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[Option[RecordMetadata]]()

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        when(reporter.Types).thenCallRealMethod()
        import reporter.Types._

        when(reporter.report(any[com.ubirch.models.Error]())).thenReturn {
          promiseTestSuccess.completeWith(Future.successful(Some(recordData)))
          promiseTestSuccess.future
        }

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {
                Future.failed(ParsingIntoEventLogException("OH OH", PipeData(v1, None)))
              }
            }
          }
        }

        val consumer = new DefaultStringConsumer(
          ConfigFactory.load(),
          lifeCycle,
          recordsManager
        ) {
          override def consumerConfigs: ConfigProperties = {
            Configs(
              bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
              groupId = "My_Group_ID",
              enableAutoCommit = false,
              autoOffsetReset = OffsetResetStrategy.EARLIEST
            )
          }

        }.get()

        consumer.setUseSelfAsRebalanceListener(false)

        consumer.startPolling()

        val response = await(promiseTestSuccess.future, 2 seconds)
        assert(response == Option(recordData))
        assert(response.isDefined)

      }

    }

    "run an NeedForPauseException and pause and then unpause" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toJson

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[Unit]()

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        when(reporter.Types).thenCallRealMethod()
        import reporter.Types._

        when(reporter.report(any[com.ubirch.models.Error]())).thenReturn {
          Future.successful(
            Option(
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
          )
        }

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {

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
          recordsManager
        ) {
          override def consumerConfigs: ConfigProperties = {
            Configs(
              bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
              groupId = "My_Group_ID",
              enableAutoCommit = false,
              autoOffsetReset = OffsetResetStrategy.EARLIEST
            )
          }

        }.get()

        consumer.setUseSelfAsRebalanceListener(false)

        consumer.startPolling()

        Thread.sleep(5000)

        assert(consumer.getPausedHistory.get() >= 1)

        Thread.sleep(3000)

        assert(consumer.getUnPausedHistory.get() >= 1)

      }

    }

    "run an NeedForPauseException and pause and then unpause when throttling" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toJson

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val promiseTestSuccess = Promise[Unit]()

        val lifeCycle = mock[DefaultLifecycle]

        val reporter = mock[Reporter]

        when(reporter.Types).thenCallRealMethod()
        import reporter.Types._

        when(reporter.report(any[com.ubirch.models.Error]())).thenReturn {
          Future.successful(
            Option(
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
          )
        }

        val executionFamily = mock[ExecutorFamily]

        val recordsManager = new DefaultConsumerRecordsManager(reporter, executionFamily, counter, config) {
          override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
            new Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] {
              override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {

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
          recordsManager
        ) {
          override def consumerConfigs: ConfigProperties = {
            Configs(
              bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
              groupId = "My_Group_ID",
              enableAutoCommit = false,
              autoOffsetReset = OffsetResetStrategy.EARLIEST
            )
          }

        }.get()

        consumer.setUseSelfAsRebalanceListener(false)

        consumer.setDelaySingleRecord(10 millis)
        consumer.setDelayRecords(1000 millis)

        consumer.startPolling()

        Thread.sleep(5000)

        assert(consumer.getPausedHistory.get() >= 1)

        Thread.sleep(3000)

        assert(consumer.getUnPausedHistory.get() >= 1)

      }

    }

    /*
    By allowing Prometheus with unique names, the counters/summaries start failing when more than one object is used with the same name

    "spawn 2 consumers to test rebalancing of 1 partition" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val partitions = 1
        createCustomTopic("com.ubirch.eventlog", partitions = partitions)

        val consumer_0 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(5000)

        val consumer_1 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(5000)

        assert(consumer_0.partitionsRevoked.get().size == partitions)
        assert(consumer_0.partitionsAssigned.get().size == partitions)
        assert(consumer_1.partitionsRevoked.get().isEmpty)
        assert(consumer_1.partitionsAssigned.get().isEmpty)

      }

    }

    "spawn 2 consumers to test rebalancing of 10 partitions" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val partitions = 10
        createCustomTopic("com.ubirch.eventlog", partitions = partitions)

        val consumer_0 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(5000)

        val consumer_1 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(7000)

        assert(consumer_0.partitionsRevoked.get().size == partitions)
        assert(consumer_0.partitionsAssigned.get().size == partitions / 2)
        assert(consumer_1.partitionsRevoked.get().isEmpty)
        assert(consumer_1.partitionsAssigned.get().size == partitions / 2)

      }

    }

    "spawn 3 consumers to test rebalancing of 10 partitions" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val partitions = 10
        createCustomTopic("com.ubirch.eventlog", partitions = partitions)

        val consumer_0 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(5000)

        val consumer_1 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(7000)

        assert(consumer_0.partitionsRevoked.get().size == partitions)
        assert(consumer_0.partitionsAssigned.get().size == partitions / 2)
        assert(consumer_1.partitionsRevoked.get().isEmpty)
        assert(consumer_1.partitionsAssigned.get().size == partitions / 2)

        val consumer_2 = spawn(kafkaConfig.kafkaPort)

        Thread.sleep(7000)

        assert(consumer_0.partitionsRevoked.get().size == partitions / 2)
        assert(consumer_0.partitionsAssigned.get().size == partitions - 6)
        assert(consumer_1.partitionsRevoked.get().size == partitions / 2)
        assert(consumer_1.partitionsAssigned.get().size == partitions - 7)
        assert(consumer_2.partitionsRevoked.get().isEmpty)
        assert(consumer_2.partitionsAssigned.get().size == partitions - 7)

      }

    }*/

    /*    "X" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val entity = Entities.Events.eventExample()
        val entityAsString = entity.toString

        createCustomTopic("com.ubirch.eventlog", partitions = 2)

        (1 to 2000).foreach(_ => publishStringMessageToKafka("com.ubirch.eventlog", entityAsString))

        spawn2
        spawn2

        Thread.sleep(10000)

      }

    }*/

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
