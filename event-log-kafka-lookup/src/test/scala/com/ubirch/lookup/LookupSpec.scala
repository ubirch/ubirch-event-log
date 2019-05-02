package com.ubirch.lookup

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.lookup.models.{ Found, NotFound }
import com.ubirch.lookup.services.LookupServiceBinder
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.native.JsonMethods._

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

  val insertSql =
    """
      |INSERT INTO events (id, customer_id, service_class, category, event, year, month , day , hour, minute, second , milli, signature, nonce) VALUES ('c29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', 'UPA', '{
      |   "hint":0,
      |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
      |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
      |   "version":34
      |}', 2019, 5, 2, 19, 439, 16, 0, '0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05', '34376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

  implicit val se = new StringSerializer

  "Lookup Spec" must {

    "consume and process successfully when Found" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertSql
        )
      )

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup-request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "c29tZSBieXRlcyEAAQIDnw=="
        publishToKafka(messageEnvelopeTopic, key, value)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.FromString[Found](readMessage).get

        val data = parse(
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

        assert(lookupRes == Found(key, data))

      }

    }

    "consume and process successfully when NotFound" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup-request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = UUIDHelper.randomUUID.toString
        val value = "c29tZSBieXRlcyEAAQIDnw=="
        publishToKafka(messageEnvelopeTopic, key, value)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.FromString[NotFound](readMessage).get

        assert(lookupRes == NotFound(key))

      }

    }

    "consume and process successfully when NotFound when key and value are empty" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.lookup-request"
        val eventLogTopic = "com.ubirch.eventlog.lookup_response"

        val key = ""
        val value = ""

        publishToKafka(messageEnvelopeTopic, key, value)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val lookupRes = LookupJsonSupport.FromString[NotFound](readMessage).get

        assert(lookupRes == NotFound(key))

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
        """.stripMargin
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }

}
