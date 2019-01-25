package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.sdk.EventLogging
import com.ubirch.services.kafka.producer.{ Configs, StringProducer }
import com.ubirch.util.Implicits.configsToProps
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.mockito.MockitoSugar

case class Hello(name: String)

class EventLoggingSpec extends TestBase with MockitoSugar with LazyLogging {

  "EventLogging" must {

    "not be created when props are empty" in {

      implicit val kafKaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val logger = new EventLogging

        import logger._

        val configs = Configs(bootstrapServers = "localhost:" + kafKaConfig.kafkaPort)

        setStringProducer(new StringProducer(configs))

        val logged = logger.log(Hello("Hello")).commit

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe logged.toString

        getStringProducer.producer.close()
      }

    }

  }

}
