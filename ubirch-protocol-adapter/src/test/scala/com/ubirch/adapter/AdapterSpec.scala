package com.ubirch.adapter

import java.util.UUID
import java.util.concurrent.TimeoutException

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.adapter.services.AdapterServiceBinder
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopeConsumer
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.EventLog
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._

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

  implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
  implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

  "Adapter Spec" must {

    "consume message envelope and publish event log with hint = 0" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val eventLogTopic = "com.ubirch.eventlog"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, 3)
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
        val eventBytes = SigningHelper.getBytesFromString(fromJsonNode(pm.getPayload).toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        assert(eventLog.event == JInt(3))
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == ServiceTraits.ADAPTER_CATEGORY)

      }

    }

    "consume message envelope and publish event log with hint != 0" in {

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

        assertThrows[TimeoutException](consumeFirstStringMessageFrom(eventLogTopic))

      }

    }

    "consume message envelope with empty customer id and publish it to error topic" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val errorTopic = "com.ubirch.eventlog.error"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 2, 3)

        val ctxt = JObject("customerId" -> JString(""))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[MessageEnvelopeConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EventLogJsonSupport.FromString[EventLog](readMessage).get
        val error = EventLogJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        assert(error.value == entity1.toString)

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with empty customer id and publish it to error topic 2" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val errorTopic = "com.ubirch.eventlog.error"

        val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, null)

        val ctxt = JObject("customerId" -> JString("my customer id"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[MessageEnvelopeConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EventLogJsonSupport.FromString[EventLog](readMessage).get
        val error = EventLogJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error Parsing Into Event Log")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        assert(error.value == entity1.toString)

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with no customer id and publish it to error topic" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val errorTopic = "com.ubirch.eventlog.error"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 2, 3)

        val ctxt = JObject("otherValue" -> JString("what!"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[MessageEnvelopeConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EventLogJsonSupport.FromString[EventLog](readMessage).get
        val error = EventLogJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        assert(error.value == entity1.toString)

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with no customer id and publish it to error topic 2" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val errorTopic = "com.ubirch.eventlog.error"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 2, 3)

        val ctxt = JObject()
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[MessageEnvelopeConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EventLogJsonSupport.FromString[EventLog](readMessage).get
        val error = EventLogJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        assert(error.value == entity1.toString)

        assert(error.serviceName == "event-log-service")

      }

    }

  }

}
