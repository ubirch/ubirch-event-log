package com.ubirch.encoder

import java.util.UUID
import java.util.concurrent.TimeoutException

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.services.EncoderServiceBinder
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.{ All, BytesConsumer }
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ EventLog, LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }
import org.bouncycastle.util.encoders.Base64
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
  import EncoderUtil.getDigest
  import LookupKey._

  implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
  implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

  val messageEnvelopeTopic = "json.to.sign"
  val eventLogTopic = "com.ubirch.eventlog.dispatch_request"
  val errorTopic = "com.ubirch.eventlog.error"

  "Encoder Spec for MessageEnvelope" must {

    "consume message envelope and publish event log with hint = 0 with lookup key a lot of messages" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val range = 1 to 300
        range.foreach { x =>
          val pmId = getDigest(Array(x.toByte))
          val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
          pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
          val customerId = UUID.randomUUID().toString
          val ctxt = JObject("customerId" -> JString(customerId))
          val entity1 = MessageEnvelope(pm, ctxt)

          publishToKafka(messageEnvelopeTopic, entity1)
        }

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(500)

        val readMessages = readMessage(eventLogTopic, maxToRead = range.size)

        assert(readMessages.size == range.size)

      }

    }

    "consume message envelope and publish event log with hint = 0 with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(7000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        val lookupKeys = Seq(
          LookupKey(
            Values.SIGNATURE,
            Values.UPP_CATEGORY,
            pmId.asKey,
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }.asValue
            }
          ).categoryAsKeyLabel
            .nameAsValueLabelForAll,
          LookupKey(
            name = Values.DEVICE_ID,
            category = Values.DEVICE_CATEGORY,
            key = pm.getUUID.toString.asKey,
            value = Seq(pmId.asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.UPP_CATEGORY)
        )

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == Values.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

      }

    }

    "consume message envelope and publish event log with hint = 0 with no lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readM = readMessage(eventLogTopic).headOption.getOrElse("")
        val eventLog = EncoderJsonSupport.FromString[EventLog](readM).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == Values.UPP_CATEGORY)

      }

    }

    "consume message envelope and publish event log with hint != 0" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 2, pmId)
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        assertThrows[TimeoutException](consumeFirstStringMessageFrom(eventLogTopic))

      }

    }

    "consume message envelope with empty customer id and publish it to error topic" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)

        val ctxt = JObject("customerId" -> JString(""))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readM = readMessage(errorTopic).headOption.getOrElse("")
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readM).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: No customerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with customer id and null payload and publish it to error topic" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, null)

        val ctxt = JObject("customerId" -> JString("my customer id"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: Payload not found or is empty")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with customer id and invalid payload and publish it to error topic" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, 3)

        val ctxt = JObject("customerId" -> JString("my customer id"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: Error building payload | Payload is not valid: 3")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with customer id and invalid payload and publish it to error topic 2" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, Base64.toBase64String(Array.fill(101)(0)))

        val ctxt = JObject("customerId" -> JString("my customer id"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: Error building payload | Payload length is not valid: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with no customer id and publish it to error topic" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)

        val ctxt = JObject("otherValue" -> JString("what!"))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: No customerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope with no customer id and publish it to error topic 2" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + config.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)

        val ctxt = JObject()
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)
        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: No customerId found")

        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

        //ubirch Packet is not with underscores.
        assert(error.value == EncoderJsonSupport.stringify(EncoderJsonSupport.to(entity1)))

        assert(error.serviceName == "event-log-service")

      }

    }

    "consume message envelope and publish event log with hint = 0 with no lookup key when no chain" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        val lookupKeys = Seq(
          LookupKey(
            Values.SIGNATURE,
            Values.UPP_CATEGORY,
            pmId.asKey,
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }.asValue
            }
          ).categoryAsKeyLabel
            .nameAsValueLabelForAll,
          LookupKey(
            name = Values.DEVICE_ID,
            category = Values.DEVICE_CATEGORY,
            key = pm.getUUID.toString.asKey,
            value = Seq(pmId.asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.UPP_CATEGORY)
        )

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == Values.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

      }

    }

    "consume message envelope and publish event log with hint = 0 with no lookup key when there is a chain" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        pm.setChain(org.bouncycastle.util.Strings.toByteArray("this is my chain"))

        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)

        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)

        val lookupKeys = Seq(
          LookupKey(
            Values.SIGNATURE,
            Values.UPP_CATEGORY,
            pmId.asKey,
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }.asValue
            }
          ).categoryAsKeyLabel
            .nameAsValueLabelForAll,
          LookupKey(
            Values.UPP_CHAIN,
            Values.CHAIN_CATEGORY,
            pmId.asKey,
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("this is my chain")
              }.asValue
            }
          ).withKeyLabel(Values.UPP_CATEGORY)
            .categoryAsValueLabelForAll,
          LookupKey(
            name = Values.DEVICE_ID,
            category = Values.DEVICE_CATEGORY,
            key = pm.getUUID.toString.asKey,
            value = Seq(pmId.asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.UPP_CATEGORY)
        )

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == Values.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

      }

    }

    "consume message envelope and publish event log with hint = 250,251,252" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        // Disable UPP
        val disablePmId = getDigest(Array(1.toByte))
        val disablePm = new ProtocolMessage(1, UUID.randomUUID(), 250, disablePmId)
        disablePm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId1 = UUID.randomUUID().toString
        val ctxt1 = JObject("customerId" -> JString(customerId1))
        val entity1 = MessageEnvelope(disablePm, ctxt1)

        // Enable UPP
        val enablePmId = getDigest(Array(2.toByte))
        val enablePm = new ProtocolMessage(1, UUID.randomUUID(), 251, enablePmId)
        enablePm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId2 = UUID.randomUUID().toString
        val ctxt2 = JObject("customerId" -> JString(customerId2))
        val entity2 = MessageEnvelope(enablePm, ctxt2)

        // Delete UPP
        val deletePmId = getDigest(Array(3.toByte))
        val deletePm = new ProtocolMessage(1, UUID.randomUUID(), 252, deletePmId)
        deletePm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId3 = UUID.randomUUID().toString
        val ctxt3 = JObject("customerId" -> JString(customerId3))
        val entity3 = MessageEnvelope(deletePm, ctxt3)

        publishToKafka(messageEnvelopeTopic, entity1)
        publishToKafka(messageEnvelopeTopic, entity2)
        publishToKafka(messageEnvelopeTopic, entity3)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(7000)

        var disableMessageCount = 0
        var enableMessageCount = 0
        var deleteMessageCount = 0
        val readMessages = consumeNumberStringMessagesFrom(eventLogTopic, 3)
        readMessages.foreach { readMessage =>
          val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
          eventLog.category match {
            case Values.UPP_DISABLE_CATEGORY =>
              val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](disablePm).get.toString)
              val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)
              assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](disablePm).get)
              assert(eventLog.customerId == customerId1)
              assert(eventLog.signature == signature)
              assert(eventLog.category == Values.UPP_DISABLE_CATEGORY)
              assert(eventLog.nonce.nonEmpty)
              disableMessageCount += 1
            case Values.UPP_ENABLE_CATEGORY =>
              val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](enablePm).get.toString)
              val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)
              assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](enablePm).get)
              assert(eventLog.customerId == customerId2)
              assert(eventLog.signature == signature)
              assert(eventLog.category == Values.UPP_ENABLE_CATEGORY)
              assert(eventLog.nonce.nonEmpty)
              enableMessageCount += 1
            case Values.UPP_DELETE_CATEGORY =>
              val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](deletePm).get.toString)
              val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)
              assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](deletePm).get)
              assert(eventLog.customerId == customerId3)
              assert(eventLog.signature == signature)
              assert(eventLog.category == Values.UPP_DELETE_CATEGORY)
              assert(eventLog.nonce.nonEmpty)
              deleteMessageCount += 1
          }
        }

        assert(disableMessageCount == 1)
        assert(enableMessageCount == 1)
        assert(deleteMessageCount == 1)
      }
    }

  }

  "Encoder Spec for BlockchainResponse" must {

    "consume blockchain response and publish event log with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

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
        consumer.setConsumptionStrategy(All)

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
            Values.ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK,
            Values.PUBLIC_CHAIN_CATEGORY,
            "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0".asKey,
            Seq("e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19".asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.MASTER_TREE_CATEGORY)
        ))
        assert(Option(eventLog.category) == eventLog.lookupKeys.map(_.name).headOption)
        assert(eventLog.customerId == Values.UBIRCH)

      }

    }
  }

  "Encoder Spec for Public Key" must {

    "consume public key response and publish event log with lookup key" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val publicKey =
          """
            |{
            |  "id": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
            |  "public_key": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19"
            |}
          """.stripMargin

        publishStringMessageToKafka(messageEnvelopeTopic, publicKey)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get

        assert(eventLog.category == Values.PUB_KEY_CATEGORY)
        assert(eventLog.event == parse(publicKey))
        assert(eventLog.id == "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19")
        assert(eventLog.lookupKeys == Seq(
          LookupKey(
            Values.PUB_KEY_CATEGORY,
            Values.PUB_KEY_CATEGORY,
            "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19".asKey,
            Seq("51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0".asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.PUB_KEY_CATEGORY)
        ))
        assert(Option(eventLog.category) == eventLog.lookupKeys.map(_.name).headOption)
        assert(eventLog.customerId == "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0")

      }

    }
  }

  "Encoder Spec for unsupported message" must {

    "fail when message not supported" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val blockchainResp = """{"hello": "hola"}"""

        publishStringMessageToKafka(messageEnvelopeTopic, blockchainResp)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val readMessage = consumeFirstStringMessageFrom(errorTopic)

        val eventLog: EventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get
        val error = EncoderJsonSupport.FromJson[com.ubirch.models.Error](eventLog.event).get

        assert(error.message == "Error in the Encoding Process: {\"hello\":\"hola\"}")
        assert(error.exceptionName == "com.ubirch.encoder.util.Exceptions.EncodingException")

      }
    }
  }

  "Encoder Spec reading from multiple topics" must {

    "consume messages from different topics" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val topic2 = "topic2"

      val topics = List(messageEnvelopeTopic, topic2)

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val pmId = getDigest(Array(3.toByte))
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)

        publishToKafka(messageEnvelopeTopic, entity1)
        publishToKafka(topic2, entity1)

        //Consumer
        val consumer = InjectorHelper.get[BytesConsumer]
        consumer.setTopics(topics.toSet)
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(10000)

        val eventBytes = SigningHelper.getBytesFromString(EncoderJsonSupport.ToJson[ProtocolMessage](pm).get.toString)
        val signature = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes)
        val lookupKeys = Seq(
          LookupKey(
            Values.SIGNATURE,
            Values.UPP_CATEGORY,
            pmId.asKey,
            Seq {
              org.bouncycastle.util.encoders.Base64.toBase64String {
                org.bouncycastle.util.Strings.toByteArray("1111")
              }.asValue
            }
          ).categoryAsKeyLabel
            .nameAsValueLabelForAll,
          LookupKey(
            name = Values.DEVICE_ID,
            category = Values.DEVICE_CATEGORY,
            key = pm.getUUID.toString.asKey,
            value = Seq(pmId.asValue)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.UPP_CATEGORY)
        )

        //UPP Event Check
        val readMessage = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog = EncoderJsonSupport.FromString[EventLog](readMessage).get

        assert(eventLog.event == EncoderJsonSupport.ToJson[ProtocolMessage](pm).get)
        assert(eventLog.customerId == customerId)
        assert(eventLog.signature == signature)
        assert(eventLog.category == Values.UPP_CATEGORY)
        assert(eventLog.lookupKeys == lookupKeys)
        assert(eventLog.nonce.nonEmpty)

        //Acct Event Check
        val readMessage1 = consumeFirstStringMessageFrom(eventLogTopic)
        val eventLog1 = EncoderJsonSupport.FromString[EventLog](readMessage1).get

        val eventBytes1 = SigningHelper.getBytesFromString(eventLog1.event.toString)
        val signature1 = SigningHelper.signAndGetAsHex(InjectorHelper.get[Config], eventBytes1)

        assert(eventLog1.customerId == customerId)
        assert(eventLog1.category == Values.ACCT_CATEGORY)
        assert(eventLog1.signature == signature1)
        assert(eventLog1.nonce.nonEmpty)

      }

    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
