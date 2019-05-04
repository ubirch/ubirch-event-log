package com.ubirch.lookup

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.lookup.models.{ Payload, QueryType, Signature }
import com.ubirch.lookup.services.LookupServiceBinder
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.JsonAST.JNull

class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new LookupServiceBinder {
  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(
          "eventLog.kafkaConsumer.bootstrapServers",
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        )
        .withValue(
          "eventLog.kafkaProducer.bootstrapServers",
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        )
    }
  })
}))

class LookupSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  val insertEventSql =
    s"""
      |INSERT INTO events (id, customer_id, service_class, category, event, event_time, year, month, day, hour, minute, second, milli, signature, nonce)
      | VALUES ('c29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', '${ServiceTraits.ADAPTER_CATEGORY}', '{
      |   "hint":0,
      |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
      |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
      |   "version":34
      |}', '2019-01-29T17:00:28.333Z', 2019, 5, 2, 19, 439, 16, 0, '0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05',
      |'34376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

  val insertLookupSql =
    s"""
      |INSERT INTO lookups (name, category, key, value) VALUES ('${Signature.value}', '${ServiceTraits.ADAPTER_CATEGORY}', 'c29tZSBieXRlcyEAAQIDnw==', '5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==');
    """.stripMargin

  implicit val se: StringSerializer = new StringSerializer

  "Lookup Spec" must {

    "consume and process successfully when Found" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql
        )
      )

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup_request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "c29tZSBieXRlcyEAAQIDnw=="
        val queryType = Payload
        val pr = ProducerRecordHelper.toRecord(messageEnvelopeTopic, key, value, Map(QueryType.QUERY_TYPE_HEADER -> queryType.value))
        publishToKafka(pr)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.getJValue(readMessage)

        val data =
          """
            |{
            |   "hint":0,
            |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
            |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
            |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
            |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
            |   "version":34
            |}
          """.stripMargin

        assert(lookupRes == LookupJsonSupport.getJValue(data))

      }

    }

    "consume and process successfully when NotFound" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup_request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "c29tZSBieXRlcyEAAQIDnw=="
        val queryType = Payload
        val pr = ProducerRecordHelper.toRecord(messageEnvelopeTopic, key, value, Map(QueryType.QUERY_TYPE_HEADER -> queryType.value))
        publishToKafka(pr)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.getJValue(readMessage)

        assert(lookupRes == JNull)

      }

    }

    "consume and process successfully when NotFound when key and value are empty" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup_request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = ""
        val value = ""
        val queryType = Payload
        val pr = ProducerRecordHelper.toRecord(messageEnvelopeTopic, key, value, Map(QueryType.QUERY_TYPE_HEADER -> queryType.value))
        publishToKafka(pr)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.getJValue(readMessage)

        assert(lookupRes == JNull)

      }

    }

    "consume and process successfully when Found with Type Signature when no lookup found" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql
        )
      )

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup_request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "c29tZSBieXRlcyEAAQIDnw=="
        val queryType = Signature
        val pr = ProducerRecordHelper.toRecord(messageEnvelopeTopic, key, value, Map(QueryType.QUERY_TYPE_HEADER -> queryType.value))
        publishToKafka(pr)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.getJValue(readMessage)

        val data = LookupJsonSupport.getJValue(
          """
            |{
            |   "hint":0,
            |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
            |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
            |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
            |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
            |   "version":34
            |}
          """.stripMargin
        )

        assert(lookupRes == JNull)

      }

    }

    "consume and process successfully when Found with Type Signature" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql,
          insertLookupSql
        )
      )

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup_request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg=="
        val queryType = Signature
        val pr = ProducerRecordHelper.toRecord(messageEnvelopeTopic, key, value, Map(QueryType.QUERY_TYPE_HEADER -> queryType.value))
        publishToKafka(pr)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.getJValue(readMessage)

        val data = LookupJsonSupport.getJValue(
          """
            |{
            |   "hint":0,
            |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
            |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
            |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
            |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
            |   "version":34
            |}
          """.stripMargin
        )

        assert(lookupRes == data)

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
          |    nonce text,
          |    PRIMARY KEY ((id, category), year, month, day, hour)
          |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin,
        "drop table if exists lookups;",
        """
          |create table if not exists lookups (
          |    key text,
          |    value text,
          |    name text,
          |    category text,
          |    PRIMARY KEY ((value, category), name)
          |);
        """.stripMargin
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }

}
