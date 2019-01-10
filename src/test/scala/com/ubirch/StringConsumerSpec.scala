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

        val entity = Entities.Events.eventExample()
        val entityAsString = Entities.Events.eventExampleAsString(entity)

        publishStringMessageToKafka("com.ubirch.eventlog", entityAsString)

        val configs = Configs(
          bootstrapServers = "localhost:" + config.kafkaPort,
          groupId = "My_Group_ID",
          autoOffsetReset =
            OffsetResetStrategy.EARLIEST
        )
        val consumer = get[StringConsumer].withProps(configs)

        consumer.startPolling()

        Thread.sleep(5000)

        val events = get[Events]

        val res = events.selectAll

        assert(await(res).nonEmpty)
        assert(await(res).headOption == Option(entity))

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
