package com.ubirch

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

class StringConsumerSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  "StringConsumerSpec" must {

    "run Executors successfully and complete expected promise" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        import InjectorHelper._

        val topic = "com.ubirch.eventlog"

        val entity1 = Entities.Events.eventExample()
        val entityAsString1 = Entities.Events.eventExampleAsString(entity1)

        publishStringMessageToKafka(topic, entityAsString1)

        //Consumer
        val configs = Configs(
          bootstrapServers = "localhost:" + config.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )
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
