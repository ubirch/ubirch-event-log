package com.ubirch.adapter

import java.util.UUID

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.adapter.services.AdapterServiceBinder
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopeConsumer
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.{ Configs => ConsumerConfigs }
import com.ubirch.models.EventLog
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util.{ EventLogJsonSupport, InjectorHelper, PortGiver }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.json4s.JsonAST.{ JInt, JObject, JString }

class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new AdapterServiceBinder {
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

class AdapterSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  implicit val se = com.ubirch.kafka.EnvelopeSerializer
  implicit val de = com.ubirch.kafka.EnvelopeDeserializer

  "Adapter Spec" must {

    "consume message and store it in cassandra" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val eventLogTopic = "com.ubirch.eventlog"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 2, 3)
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[MessageEnvelopeConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EventLogJsonSupport.FromString[EventLog](readMessage).get
        assert(eventLog.event == JInt(3))
        assert(eventLog.customerId == customerId)


      }

    }

  }

}
