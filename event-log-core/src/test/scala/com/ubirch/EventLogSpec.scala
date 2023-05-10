package com.ubirch

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import com.github.nosan.embedded.cassandra.cql.StringCqlScript
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.{ Configs, StringConsumer }
import com.ubirch.kafka.util.Exceptions.CommitTimeoutException
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.services.ServiceBinder
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.kafka.consumer.DefaultConsumerRecordsManager
import com.ubirch.util.cassandra.test.EmbeddedCassandraBase
import com.ubirch.util.{ InjectorHelper, UUIDHelper }
import io.prometheus.client.CollectorRegistry
import monix.execution.Scheduler
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecords, OffsetResetStrategy }
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class InjectorHelperImpl(bootstrapServers: String, storeLookups: Boolean = true) extends InjectorHelper(List(new ServiceBinder {
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
      .withValue(
        "eventLog.storeLookups",
        ConfigValueFactory.fromAnyRef(storeLookups)
      )
  })
}))

class EventLogSpec extends TestBase with EmbeddedCassandraBase with LazyLogging {

  val cassandra = new CassandraTest

  "EventLog components" must {

    "consume message and store it in cassandra" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entity1 = Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)

        val entityAsString1 = entity1.toJson

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

        val res1: List[EventLogRow] = await(res)

        assert(res1.nonEmpty)
        assert(res1.headOption == Option(EventLogRow.fromEventLog(entity1)))

        //Next Message
        val entity2 = Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)

        val entityAsString2 = entity2.toJson

        publishStringMessageToKafka(topic, entityAsString2)

        Thread.sleep(5000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)
        assert(res2.contains(EventLogRow.fromEventLog(entity2)))

        assert(res2.size == 2)

      }

    }

    "consume message and store it in cassandra with lookup key" in {

      import LookupKey._

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entity1 = Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)
          .withLookupKeys(Seq(LookupKey("name", "category", "key".asKey, Seq("value".asValue, "value1".asValue))))

        val entityAsString1 = entity1.toJson

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

        val res1: List[EventLogRow] = await(res)

        assert(res1.nonEmpty)
        assert(res1.headOption == Option(EventLogRow.fromEventLog(entity1)))

        //Next Message
        val entity2 = Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)

        val entityAsString2 = entity2.toJson

        publishStringMessageToKafka(topic, entityAsString2)

        Thread.sleep(5000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)
        assert(res2.contains(EventLogRow.fromEventLog(entity2)))

        assert(res2.size == 2)

        //Read Events
        val lookupKeys = InjectorHelper.get[Lookups]
        def lookupKeysRes = lookupKeys.selectAll
        //Read

        val lookupKeysRes1: List[LookupKeyRow] = await(lookupKeysRes)

        assert(lookupKeysRes1.nonEmpty)
        assert(lookupKeysRes1 == Seq(LookupKeyRow("name", "category", "key", "value1"), LookupKeyRow("name", "category", "key", "value")))

      }

    }

    "consume messages and store them in cassandra" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entities = (0 to 500).map(_ => Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)).toList

        val entitiesAsString = entities.map(_.toJson)

        entitiesAsString.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(15000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1: List[EventLogRow] = await(res)

        assert(res1.nonEmpty)

        res1 must contain theSameElementsAs entities.map(EventLogRow.fromEventLog)
        res1.size must be(entities.size)

      }

    }

    "consume messages and delete UPP in cassandra" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      val config = InjectorHelper.get[Config]

      val entities = (0 to 5).map(_ => Entities.Events.eventExample(UUIDHelper.randomUUID, Values.UPP_CATEGORY)
        .sign(config)
        .withCustomerId(UUIDHelper.randomUUID)).toList

      val eventsDAO = InjectorHelper.get[EventsDAO]

      entities.map(el => await(eventsDAO.insertFromEventLogWithoutLookups(el), 2 seconds))
      val all = await(eventsDAO.events.selectAll, 2 seconds)

      assert(all.size == entities.size)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val entitiesAsString = entities.map(_.copy(category = Values.UPP_DELETE_CATEGORY)).map(_.toJson)

        entitiesAsString.foreach { entityAsString =>
          publishStringMessageToKafka(topic, entityAsString)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(15000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1: List[EventLogRow] = await(res)

        assert(res1.isEmpty)

      }

    }

    "not insert message twice with same id unless the primary value parts don't change" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]
        val entity1 = Entities.Events.eventExample()
          .sign(config)
          .withCustomerId(UUIDHelper.randomUUID)

        val entityAsString1 = entity1.toJson

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]

        consumer.startPolling()
        //Consumer

        Thread.sleep(7000)

        //Read Events
        val events = InjectorHelper.get[Events]
        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)
        assert(res1.contains(EventLogRow.fromEventLog(entity1)))

        //Next Message

        publishStringMessageToKafka(topic, entityAsString1)

        Thread.sleep(7000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)

        assert(res2.contains(EventLogRow.fromEventLog(entity1)))

        assert(res2.size == 1)

        //Next Message with same id but different stuff inside

        val entity1Modified = entity1.copy(category = "This is a brand new cat", signature = "This is another signature")
        val entityAsString1Modified = entity1Modified.toJson

        publishStringMessageToKafka(topic, entityAsString1Modified)

        Thread.sleep(7000) //Wait for next consumption

        val res3 = await(res)

        assert(res3.nonEmpty)

        assert(res3.contains(EventLogRow.fromEventLog(entity1)))

        assert(res3.size == 2)

      }
    }

    "consume message and store it in cassandra less the error" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val InjectorHelper = new InjectorHelperImpl("localhost:" + kafkaConfig.kafkaPort)

      withRunningKafka {

        val topic = "com.ubirch.eventlog"

        val config = InjectorHelper.get[Config]

        val entities = (0 to 10).map(_ =>
          Entities.Events.eventExample()
            .sign(config)
            .withCustomerId(UUIDHelper.randomUUID)).toList

        val entitiesAsStringWithErrors = entities.map(_.toJson) ++ //Malformed data
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

        val res1: List[EventLogRow] = await(res)

        assert(res1.nonEmpty)

        res1 must contain theSameElementsAs entities.map(EventLogRow.fromEventLog)
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

        val entity1 = Entities.Events.eventExample().withCustomerId(UUIDHelper.randomUUID)
        val entityAsString1 = entity1.toJson

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val attempts = new CountDownLatch(3)

        implicit val scheduler: Scheduler = Scheduler(InjectorHelper.get[ExecutionContext])

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def oneFactory(currentPartitionIndex: Int, currentPartition: TopicPartition, allPartitions: Set[TopicPartition], consumerRecords: ConsumerRecords[String, String]): ProcessRecordsOne = {
            new ProcessRecordsOne(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
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
        consumer.setForceExit(false)
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

        val entity1 = Entities.Events.eventExample().withCustomerId(UUIDHelper.randomUUID)
        val entityAsString1 = entity1.toJson

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val attempts = new CountDownLatch(4)

        implicit val scheduler: Scheduler = Scheduler(InjectorHelper.get[ExecutionContext])

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def oneFactory(currentPartitionIndex: Int, currentPartition: TopicPartition, allPartitions: Set[TopicPartition], consumerRecords: ConsumerRecords[String, String]): ProcessRecordsOne = {
            new ProcessRecordsOne(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
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
        consumer.setForceExit(false)
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

        val entity1 = Entities.Events.eventExample().withCustomerId(UUIDHelper.randomUUID)
        val entityAsString1 = entity1.toJson

        publishStringMessageToKafka(topic, entityAsString1)

        val controller = InjectorHelper.get[DefaultConsumerRecordsManager]

        val committed = new CountDownLatch(1)
        val failedProcesses = new CountDownLatch(3)
        var committedN = 0

        implicit val scheduler: Scheduler = Scheduler(InjectorHelper.get[ExecutionContext])

        //Consumer
        val consumer: StringConsumer = new StringConsumer {
          override def oneFactory(currentPartitionIndex: Int, currentPartition: TopicPartition, allPartitions: Set[TopicPartition], consumerRecords: ConsumerRecords[String, String]): ProcessRecordsOne = {
            new ProcessRecordsOne(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                failedProcesses.countDown()
                if (failedProcesses.getCount == 1) {
                  val f = super.commitFunc()
                  failedProcesses.countDown()
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
        consumer.setForceExit(false)
        consumer.setProps(configs)
        consumer.onPostCommit(i => committedN = i)

        consumer.startPolling()
        //Consumer

        committed.await(5000, TimeUnit.MILLISECONDS)
        failedProcesses.await(5000, TimeUnit.MILLISECONDS)
        assert(committedN == 1)
        assert(committed.getCount == 0)
        assert(failedProcesses.getCount == 0)

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(List(new StringCqlScript("TRUNCATE events;"), new StringCqlScript("TRUNCATE lookups;")))
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    cassandra.executeScripts(List(
      new StringCqlScript(
        "CREATE KEYSPACE IF NOT EXISTS event_log WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"
      ),
      new StringCqlScript("USE event_log;"),
      new StringCqlScript("drop table if exists events;"),
      new StringCqlScript("""
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
          |    nonce text,
          |    PRIMARY KEY ((id, category), year, month, day, hour)
          |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin),
      new StringCqlScript("drop table if exists lookups;"),
      new StringCqlScript("""
          |create table if not exists lookups (
          |    key text,
          |    value text,
          |    name text,
          |    category text,
          |    PRIMARY KEY ((value, category), name)
          |);
        """.stripMargin)
    ))
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }
}
