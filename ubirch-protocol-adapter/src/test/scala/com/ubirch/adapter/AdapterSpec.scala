package com.ubirch.adapter

import java.util.UUID
import java.util.concurrent.TimeoutException

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.adapter.services.AdapterServiceBinder
import com.ubirch.adapter.util.AdapterJsonSupport
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.models.{ EventLog, LookupKey }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods.parse

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

class AdapterSpec extends TestBase with LazyLogging {

  implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
  implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

  "Adapter Spec for MessageEnvelope" must {

    "consume message envelope and publish event log with hint = 0 with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val eventLogTopic = "com.ubirch.eventlog"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, 3)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(AdapterJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        val lookupKeys = Seq(
          LookupKey(
            "signature",
            ServiceTraits.ADAPTER_CATEGORY,
            "3",
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }
            }
          )
        )

        assert(eventLog.event == AdapterJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == ServiceTraits.ADAPTER_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)

      }

    }

    "consume message envelope and publish event log with hint = 0 with no lookup key" in {

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
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(AdapterJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        assert(eventLog.event == AdapterJsonSupport.ToJson[ProtocolMessage](pm).get)
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
        val consumer = InjectorHelper.get[BytesConsumer]
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
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val error = AdapterJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == AdapterJsonSupport.stringify(AdapterJsonSupport.to(entity1)))

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
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val error = AdapterJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error Parsing Into Event Log")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == AdapterJsonSupport.stringify(AdapterJsonSupport.to(entity1)))

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
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val error = AdapterJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == AdapterJsonSupport.stringify(AdapterJsonSupport.to(entity1)))

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
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val error = AdapterJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == AdapterJsonSupport.stringify(AdapterJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

  }

  "Adapter Spec for BlockchainResponse" must {

    "consume message envelope and publish event log with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"
        val eventLogTopic = "com.ubirch.eventlog"

        val blockchainResp =
          """
            |{
            |  "status": "added",
            |  "txid": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
            |  "message": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
            |  "blockchain": "ethereum",
            |  "network_info": "Rinkeby Testnet Network",
            |  "network_type": "testnet",
            |  "created": "2019-05-07T21:30:14.421095"
            |}
          """.stripMargin

        publishStringMessageToKafka(messageEnvelopeTopic, blockchainResp)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get

        assert(eventLog.category == "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK")
        assert(eventLog.event == parse(blockchainResp))
        assert(eventLog.id == "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0")
        assert(eventLog.lookupKeys == Seq(
          LookupKey(
            "blockchain_tx_id",
            "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK",
            "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
            Seq("51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0")
          )
        ))

      }

    }
  }

  "Adapter Spec for unsupported message" must {

    "consume message envelope and publish event log with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val errorTopic = "com.ubirch.eventlog.error"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.messageenvelope"

        val blockchainResp = """{"hello": "hola"}"""

        publishStringMessageToKafka(messageEnvelopeTopic, blockchainResp)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)

        val eventLog: EventLog = AdapterJsonSupport.FromString[EventLog](readMessage).get
        val error = AdapterJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error Parsing Into Event Log")
        assert(error.exceptionName == "com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException")
        //        assert(error.value == AdapterJsonSupport.stringify(AdapterJsonSupport.to(blockchainResp)))

      }
    }
  }

}
