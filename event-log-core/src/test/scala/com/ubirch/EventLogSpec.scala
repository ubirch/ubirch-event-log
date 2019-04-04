package com.ubirch

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.{ Configs, StringConsumer }
import com.ubirch.kafka.util.Exceptions.CommitTimeoutException
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.Events
import com.ubirch.services.ServiceBinder
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.kafka.consumer.DefaultConsumerRecordsManager
import com.ubirch.util.{ InjectorHelper, PortGiver }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecords, OffsetResetStrategy }
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.ExecutionContext
import scala.language.{ implicitConversions, postfixOps }

class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new ServiceBinder {
  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = super.conf
      .withValue(
        "eventLog.kafkaConsumer.bootstrapServers",
        ConfigValueFactory.fromAnyRef(bootstrapServers)
      )
      .withValue(
        "eventLog.kafkaProducer.bootstrapServers",
        ConfigValueFactory.fromAnyRef(bootstrapServers)
      )
  })
}))

class EventLogSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  "EventLog components" must {

    "consume message and store it in cassandra" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entity1 = Entities.Events.eventExample().sign(config)
        val entityAsString1 = entity1.toString

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)
        assert(res1.headOption == Option(entity1))

        //Next Message
        val entity2 = Entities.Events.eventExample().sign(config)
        val entityAsString2 = entity2.toString

        publishStringMessageToKafka(topic, entityAsString2)

        Thread.sleep(5000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)
        assert(res2.contains(entity2))

        assert(res2.size == 2)

      }

    }

    "consume messages and store them in cassandra" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entities = (0 to 500).map(_ => Entities.Events.eventExample().sign(config)).toList

        val entitiesAsString = entities.map(_.toString)

        entitiesAsString.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(10000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)

        res1 must contain theSameElementsAs entities
        res1.size must be(entities.size)

      }

    }

    "not insert message twice with same id unless the primary value parts don't change" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entity1 = Entities.Events.eventExample().sign(config)
        val entityAsString1 = entity1.toString

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)
        assert(res1.contains(entity1))

        //Next Message

        publishStringMessageToKafka(topic, entityAsString1)

        Thread.sleep(5000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)

        assert(res2.contains(entity1))

        assert(res2.size == 1)

        //Next Message with same id but different stuff inside

        val entity1Modified = entity1.copy(category = "This is a brand new cat", signature = "This is another signature")
        val entityAsString1Modified = entity1Modified.toString

        publishStringMessageToKafka(topic, entityAsString1Modified)

        Thread.sleep(5000) //Wait for next consumption

        val res3 = await(res)

        assert(res3.nonEmpty)

        assert(res3.contains(entity1))

        assert(res3.size == 2)

      }
    }

    "consume message and store it in cassandra less the error" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]

        val entities = (0 to 10).map(_ => Entities.Events.eventExample().sign(config)).toList

        val entitiesAsStringWithErrors = entities.map(_.toString) ++ //Malformed data
          List("{}")

        entitiesAsStringWithErrors.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        //Read Events
        val events = InjectorHelper.get[Events]

        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)

        res1 must contain theSameElementsAs entities
        res1.size must be(entities.size)

        Thread.sleep(1000)

        assert(!consumer.getIsPaused.get())

      }

    }

    "try to commit after TimeoutException" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      val configs = Configs(
        bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
        groupId = "My_Group_ID",
        enableAutoCommit = false,
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = entity1.toString

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val attempts = new CountDownLatch(3)

        implicit val ec: ExecutionContext = InjectorHelper.get[ExecutionContext]

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                attempts.countDown()
                throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
              }
            }

          }
        }
        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setConsumerRecordsController(Some(controller))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)

        consumer.startPolling()
        //Consumer

        attempts.await(7000, TimeUnit.MILLISECONDS)
        assert(0 == attempts.getCount)

      }

    }

    "try to commit after TimeoutException and another Exception" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      val configs = Configs(
        bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
        groupId = "My_Group_ID",
        enableAutoCommit = false,
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = entity1.toString

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val attempts = new CountDownLatch(4)

        implicit val ec: ExecutionContext = InjectorHelper.get[ExecutionContext]

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                attempts.countDown()
                if (attempts.getCount == 2) {
                  attempts.countDown()
                  attempts.countDown()
                  throw new Exception("Another exception")
                } else {
                  throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
                }
              }
            }

          }
        }
        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setConsumerRecordsController(Some(controller))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)

        consumer.startPolling()
        //Consumer

        attempts.await(5000, TimeUnit.MILLISECONDS)
        assert(attempts.getCount == 0)

      }

    }

    "try to commit after TimeoutException and OK after" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      val configs = Configs(
        bootstrapServers = "localhost:" + kafkaConfig.kafkaPort,
        groupId = "My_Group_ID",
        enableAutoCommit = false,
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = entity1.toString

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val committed = new CountDownLatch(1)
        val failed = new CountDownLatch(3)
        var committedN = 0

        implicit val ec: ExecutionContext = InjectorHelper.get[ExecutionContext]

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                failed.countDown()
                if (failed.getCount == 1) {
                  val f = super.commitFunc()
                  failed.countDown()
                  committed.countDown()
                  f
                } else {
                  throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
                }
              }
            }

          }
        }
        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setConsumerRecordsController(Some(controller))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)
        consumer.onPostCommit(i => committedN = i)

        consumer.startPolling()
        //Consumer

        committed.await(5000, TimeUnit.MILLISECONDS)
        failed.await(5000, TimeUnit.MILLISECONDS)
        assert(committedN == 1)
        assert(committed.getCount == 0)
        assert(failed.getCount == 0)

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(CqlScript.statements("TRUNCATE events;"))
    Thread.sleep(5000)
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    cassandra.executeScripts(
      CqlScript.statements(
        "CREATE KEYSPACE IF NOT EXISTS event_log WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };",
        "USE event_log;",
        "drop table if exists events;",
        """
          |create table if not exists events (
          |    id text,
          |    customer_id text,
          |    service_class text,
          |    category text,
          |    signature text,
          |    event text,
          |    year int ,
          |    month int,
          |    day int,
          |    hour int,
          |    minute int,
          |    second int,
          |    milli int,
          |    event_time timestamp,
          |    PRIMARY KEY ((id, category), year, month, day, hour)
          |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }
}
