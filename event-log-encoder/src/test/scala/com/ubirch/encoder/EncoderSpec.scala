package com.ubirch.encoder

import java.util.UUID
import java.util.concurrent.TimeoutException

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.services.EncoderServiceBinder
import com.ubirch.encoder.util.EncoderJsonSupport
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

class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new EncoderServiceBinder {
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

class EncoderSpec extends TestBase with LazyLogging {

  implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
  implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

  "Encoder Spec for MessageEnvelope" must {

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
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        val lookupKeys = Seq(
          LookupKey(
            "signature",
            ServiceTraits.UPP_CATEGORY,
            "3",
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }
            }
          )
        )

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == ServiceTraits.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

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
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == ServiceTraits.UPP_CATEGORY)

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
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

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
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error Parsing Into Event Log")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

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
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

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
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "No CustomerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException")

        //Ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

  }

  "Encoder Spec for BlockchainResponse" must {

    "consume blockchain response and publish event log with lookup key" in {

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
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get

        assert(eventLog.category == "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK")
        assert(eventLog.event == parse(blockchainResp))
        assert(eventLog.id == "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0")
        assert(eventLog.lookupKeys == Seq(
          LookupKey(
            "blockchain_tx_id",
            "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK",
            "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
            Seq("e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19")
          )
        ))

      }

    }
  }

  "Encoder Spec for unsupported message" must {

    "fail when message not supported" in {

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

        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error Parsing Into Event Log")
        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException")
        //        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(blockchainResp)))

      }
    }
  }

  "Encoder Spec reading from multiple topics" must {

    "consume messages from different topics" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"

      val topic2 = "topic2"

      val topics = List(messageEnvelopeTopic, topic2)

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val eventLogTopic = "com.ubirch.eventlog"

        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, 3)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)
        publishToKafka(topic2, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(topics.toSet)

        consumer.startPolling()
        //Consumer

        Thread.sleep(10000)

        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)
        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)
        val lookupKeys = Seq(
          LookupKey(
            "signature",
            ServiceTraits.UPP_CATEGORY,
            "3",
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }
            }
          )
        )

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == ServiceTraits.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

        val readMessage1 = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog1 = EncoderJsonSupport.FromString[EventLog](readMessage1).get

        assert(eventLog1.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog1.customerId == customerId)
        assert(eventLog1.signature == signature)
        assert(eventLog1.category == ServiceTraits.UPP_CATEGORY)
        assert(eventLog1.lookupKeys == lookupKeys)
        assert(eventLog1.nonce.nonEmpty)

      }

    }
  }

}
