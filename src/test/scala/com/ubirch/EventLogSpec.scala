package com.ubirch

import java.util.Date

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Events
import com.ubirch.services.kafka._
import com.ubirch.services.kafka.consumer.{ Configs, StringConsumer }
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.InjectorHelper
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy

import scala.language.{ implicitConversions, postfixOps }

class EventLogSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  "EventLogSpec" must {

    "consume message and store it in cassandra" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset =
          OffsetResetStrategy.EARLIEST
      )

      withRunningKafka {

        import InjectorHelper._

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = Entities.Events.eventExampleAsString(entity1)

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val consumer = get[StringConsumer].withProps(configs)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        //Read Events
        val events = get[Events]
        def res = events.selectAll
        //Read

        val res1 = await(res)

        assert(res1.nonEmpty)
        assert(res1.headOption == Option(entity1))

        //Next Message
        val entity2 = Entities.Events.eventExample()
        val entityAsString2 = Entities.Events.eventExampleAsString(entity2)

        publishStringMessageToKafka(topic, entityAsString2)

        Thread.sleep(5000) //Wait for next consumption

        val res2 = await(res)

        assert(res2.nonEmpty)
        assert(res2.contains(entity2))

        assert(res2.size == 2)

      }

    }

    "not insert message twice with same id" in {
      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset =
          OffsetResetStrategy.EARLIEST
      )

      withRunningKafka {

        import InjectorHelper._

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = Entities.Events.eventExampleAsString(entity1)

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val consumer = get[StringConsumer].withProps(configs)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        //Read Events
        val events = get[Events]
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

        val entity1Modified = entity1.copy(signature = "This is another signature", created = new Date, event = entity1.event.copy(category = "This is a brand new cat"))
        val entityAsString1Modified = Entities.Events.eventExampleAsString(entity1Modified)

        publishStringMessageToKafka(topic, entityAsString1Modified)

        Thread.sleep(5000) //Wait for next consumption

        val res3 = await(res)

        assert(res3.nonEmpty)

        assert(res3.contains(entity1))

        assert(res3.size == 2)

      }
    }

  }

  override protected def beforeEach(): Unit = {
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
          |    id uuid,
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
          |    created timestamp,
          |    updated timestamp,
          |    PRIMARY KEY ((id, category), year, month, day, hour)
          |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin
      )
    )
  }

}