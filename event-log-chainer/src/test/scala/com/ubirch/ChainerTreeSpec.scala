package com.ubirch

import com.ubirch.chainer.models.Chainables.eventLogChainable
import com.ubirch.chainer.models._
import com.ubirch.chainer.services.tree.TreeMonitor
import com.ubirch.chainer.util.{ ChainerJsonSupport, PMHelper }
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util._

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.commons.codec.binary.Hex
import org.json4s.JsonAST._
import org.scalatest.Tag

import java.nio.charset.StandardCharsets
import java.util.Date

object ChainerTreeSpec {

  def getChainer(events: List[EventLog]): Chainer[EventLog, String, String] = {
    Chainer(events)
      .withBalancingProtocol(BalancingProtocol.RandomHexString())
      .withMergeProtocol(MergeProtocol.V2_HexString)
      .withGeneralGrouping
      .createSeedHashes
      .createSeedNodes(keepOrder = true)
      .createNode
  }

}

class ChainerTreeSpec extends TestBase with LazyLogging {

  import LookupKey._

  val messageEnvelopeTopic = "com.ubirch.messageenvelope"
  val eventLogTopic = "com.ubirch.eventlog"

  "Chainer Tree Spec" must {

    "create expected tree" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 2)
      val config = InjectorHelper.get[Config]

      val customerRange = 1 to 2

      val events = customerRange.map(x =>

        EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
          .withCurrentEventTime
          .withNonce(x.toString)
          .withNewId(x.toString)
          .withCategory(Values.UPP_CATEGORY)
          .sign(config))

      withRunningKafka {

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val messages: List[String] = readMessage(eventLogTopic)

        logger.info("Messages Read:" + messages)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val compressed = ChainerJsonSupport.FromJson[CompressedTreeData[String]](treeEventLog.event).get

        val hexSeeds = events
          .map(x => getHash((x.id + x.nonce)
            .getBytes(StandardCharsets.UTF_8)))
          .map(x => Hex.encodeHexString(x))
          .toList

        val root = Hex.encodeHexString(getHash(Array.concat(Hex.decodeHex(hexSeeds(0)), Hex.decodeHex(hexSeeds(1)))))
        assert(compressed.root == root)
        assert(hexSeeds == compressed.leaves)

        assert("446e8158c1e6350977c45716ae683871674f90b34cf382cd84fc4795304916bdb5200ba06c830174aaf19ba9d36b4366b6c665ec87619fe0d1addd0dc416651d" == root)
        assert("74a49c698dbd3c12e36b0b287447d833f74f3937ff132ebff7054baa18623c35a705bb18b82e2ac0384b5127db97016e63609f712bc90e3506cfbea97599f46f" == hexSeeds(0))
        assert("6ad275d26c200e81534d9996183c8748ddfabc7b0a011a90f46301626d709923474703cacab0ff8b67cd846b6cb55b23a39b03fbdfb5218eec3373cf7010a166" == hexSeeds(1))
      }

    }

    "consume, process and publish tree and event logs in Slave mode with UPPs" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun", "Earth", "Marz")
      val customerRange = 0 to 3

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.UPP_CATEGORY)
            .sign(config))
      }

      withRunningKafka {

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val maxNumberToRead = 1 /* tree */

        val messages: List[String] = readMessage(eventLogTopic)

        logger.info("Messages Read:" + messages)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val mode = Slave

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(mode)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              treeEventLog.id.asKeyWithLabel(mode.category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        //assert(chainer.getNodes.map(_.value).size == customerIds.size) for when there's explicit grouping
        assert(chainer.getNodes.map(_.value).size == 1)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Slave mode with Public Keys" in {

      import java.security.KeyPairGenerator
      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun", "Earth", "Marz")
      val customerRange = 0 to 3

      val events = customerIds.flatMap { x =>
        customerRange.map { _ =>

          val hid = UUIDHelper.randomUUID
          val pubKey = Hex.encodeHexString(KeyPairGenerator.getInstance("DSA", "SUN").generateKeyPair().getPublic.getEncoded)

          val publicKey =
            s"""
              |{
              |  "hardware_id": "$hid",
              |  "public_key": "$pubKey"
              |}
          """.stripMargin

          EventLog(ChainerJsonSupport.getJValue(publicKey))
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId(pubKey)
            .withCategory(Values.PUB_KEY_CATEGORY)
            .sign(config)

        }
      }

      withRunningKafka {

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val maxNumberToRead = 1 /* tree */

        val messages: List[String] = readMessage(eventLogTopic)

        logger.info("Messages Read:" + messages)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val mode = Slave

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(mode)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              treeEventLog.id.asKeyWithLabel(mode.category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        //assert(chainer.getNodes.map(_.value).size == customerIds.size) for when there's explicit grouping
        assert(chainer.getNodes.map(_.value).size == 1)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, mode = Master)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun", "Earth", "Marz")
        val customerRange = 0 to 3

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(JString(UUIDHelper.randomUUID.toString))
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.SLAVE_TREE_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              treeEventLog.id.asKeyWithLabel(mode.category),
              chainer.seeds.map(x => x.id.asValueWithLabel(x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        // assert(chainer.getNodes.map(_.value).size == customerIds.size) Good for when there's explicit grouping
        assert(chainer.getNodes.map(_.value).size == 1)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after time threshold is reached" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10, treeEvery = 6)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 7

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(10000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val category = Values.SLAVE_TREE_CATEGORY

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(Slave)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> Slave.value,
          HeaderNames.ORIGIN -> category,
          TreeMonitor.headersNormalCreationFromMode(Slave)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              treeEventLog.id.asKeyWithLabel(category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached" taggedAs Tag("problem") in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun")
      val customerRange = 0 to 11

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.UPP_CATEGORY)
            .sign(config))
      }

      val (e1s, e2s) = events.splitAt(7)

      withRunningKafka {

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        e1s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(5000)

        val numberOfPauses = consumer.getPausedHistory

        assert(numberOfPauses.get() > 0)

        e2s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(5000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val category = Values.SLAVE_TREE_CATEGORY

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(Slave)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> Slave.value,
          HeaderNames.ORIGIN -> category,
          TreeMonitor.headersNormalCreationFromMode(Slave)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              treeEventLog.id.asKeyWithLabel(category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 11, mode = Master)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun")
      val customerRange = 0 to 11

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(JString(UUIDHelper.randomUUID.toString))
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.SLAVE_TREE_CATEGORY)
            .sign(config))
      }

      val (e1s, e2s) = events.splitAt(7)

      withRunningKafka {

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        e1s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(7000)

        val numberOfPauses = consumer.getPausedHistory

        assert(numberOfPauses.get() > 0)

        e2s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(10000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerTreeSpec.getChainer(events)
        val node = chainer
          .compress
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(fail("No chainer compressed"))

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(LookupKey(
            mode.lookupName,
            mode.category,
            treeEventLog.id.asKeyWithLabel(mode.category),
            chainer.seeds.map(x => x.id.asValueWithLabel(x.category))
          )))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Slave splitting" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, split = true)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(7000)

        val maxNumberToRead = events.sliding(50, 50).size /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        assert(messages.size == events.sliding(50, 50).size)

      }

    }

    "consume, process and publish tree and event logs in Slave splitting and checking upgrade tree as Slave" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, split = true, treeEvery = 10, treeUpgrade = 13)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        val treeMonitor = InjectorHelper.get[TreeMonitor]
        treeMonitor.start
        //Consumer

        Thread.sleep(7000)

        val maxNumberToRead = events.sliding(50, 50).size /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)
        val messagesAsEventLogs = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get)

        val compressed = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get).map(_.event)
          .map(x => ChainerJsonSupport.FromJson[CompressedTreeData[String]](x).get)
        val nodes = compressed.map(x => Chainer.uncompress(x)(MergeProtocol.V2_HexString)).flatMap(_.toList)
        assert(nodes.map(_.value) == messagesAsEventLogs.map(_.id))
        assert(Chainer.checkConnectedness(compressed)(MergeProtocol.V2_HexString))

        val mode = Slave
        val expectedHeaders = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        )

        messagesAsEventLogs.map(_.headers).map { x =>
          assert(x == expectedHeaders)
        }

        assert(messages.size == events.sliding(50, 50).size)

        val upgrade = consumeFirstStringMessageFrom(eventLogTopic)
        val upgradeEventLog = ChainerJsonSupport.FromString[EventLog](upgrade).get

        val expectedHeadersUpgrade = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category
        //, TreeMonitor.headerExcludeStorage
        )

        assert(upgradeEventLog.headers == expectedHeadersUpgrade)

        assert(messagesAsEventLogs.reverse.headOption.map(_.id) == Option(upgradeEventLog.id))

        Thread.sleep(10000)

        assertThrows[java.util.concurrent.TimeoutException](consumeFirstStringMessageFrom(eventLogTopic))

      }

    }

    "consume, process and publish tree and event logs in Slave splitting and checking upgrade tree as Master" taggedAs new Tag("problem2") in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(
        bootstrapServers,
        messageEnvelopeTopic,
        eventLogTopic,
        split = true,
        treeEvery = 10,
        treeUpgrade = 13,
        mode = Master,
        webClientProvider = Option(new WebClientProvider)
      )
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.SLAVE_TREE_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val treeMonitor = InjectorHelper.get[TreeMonitor]
        treeMonitor.start

        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val bigBangTree = 1
        val maxNumberToReadNormalMasters = events.sliding(50, 50).size /* tree */
        val upgradeTree = 1

        val maxToRead = bigBangTree + maxNumberToReadNormalMasters + upgradeTree

        Thread.sleep(10000)

        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxToRead)
        val messagesAsEventLogs = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get)

        val compressed = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get).map(_.event)
          .map(x => ChainerJsonSupport.FromJson[CompressedTreeData[String]](x).get)
        val nodes = compressed.map(x => Chainer.uncompress(x)(MergeProtocol.V2_HexString)).flatMap(_.toList)
        assert(nodes.map(_.value) == messagesAsEventLogs.map(_.id))
        assert(Chainer.checkConnectedness(compressed)(MergeProtocol.V2_HexString))

        val mode = Master
        val expectedHeadersBigBang = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category
        )

        val expectedHeaders = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        )

        //big bang
        val bigBang = messagesAsEventLogs.headOption

        //normal trees
        val normalTrees = messagesAsEventLogs
          .tail
          .reverse
          .tail
          .reverse

        val upgradeTREE = messagesAsEventLogs
          .tail
          .reverse
          .headOption

        bigBang
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        normalTrees
          .map { x =>
            assert(x.headers == expectedHeaders)
          }

        upgradeTREE
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        //upgrade tree

        assert(messages.size == maxToRead)

        for {
          l <- normalTrees.reverse.headOption
          u <- upgradeTREE
        } yield {
          assert(l.id == u.id)
          assert(l.headers != u.headers)
          assert(l.lookupKeys != u.lookupKeys)
        }

      }

    }

    "consume, process and publish tree and event logs in Slave splitting and checking upgrade tree as Master 2" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(
        bootstrapServers,
        messageEnvelopeTopic,
        eventLogTopic,
        split = true,
        minTreeRecords = 2,
        treeEvery = 10,
        treeUpgrade = 13,
        mode = Master,
        splitSize = 5,
        webClientProvider = Option(new WebClientProvider)
      )
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 40

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.SLAVE_TREE_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val treeMonitor = InjectorHelper.get[TreeMonitor]
        treeMonitor.start

        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val bigBangTree = 1
        val maxNumberToReadNormalMasters = events.sliding(5, 5).size /* tree */
        val upgradeTree = 1

        val maxToRead = bigBangTree + maxNumberToReadNormalMasters + upgradeTree

        Thread.sleep(10000)

        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxToRead)
        val messagesAsEventLogs = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get)

        val compressed = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get).map(_.event)
          .map(x => ChainerJsonSupport.FromJson[CompressedTreeData[String]](x).get)
        val nodes = compressed.map(x => Chainer.uncompress(x)(MergeProtocol.V2_HexString)).flatMap(_.toList)
        assert(nodes.map(_.value) == messagesAsEventLogs.map(_.id))
        assert(Chainer.checkConnectedness(compressed)(MergeProtocol.V2_HexString))

        val mode = Master
        val expectedHeadersBigBang = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category
        )

        val expectedHeaders = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        )

        //big bang
        val bigBang = messagesAsEventLogs.headOption

        //normal trees
        val normalTrees = messagesAsEventLogs
          .tail
          .reverse
          .tail
          .reverse

        val upgradeTREE = messagesAsEventLogs
          .tail
          .reverse
          .headOption

        bigBang
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        normalTrees
          .map { x =>
            assert(x.headers == expectedHeaders)
          }

        upgradeTREE
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        //upgrade tree

        assert(messages.size == maxToRead)

        for {
          l <- normalTrees.reverse.headOption
          u <- upgradeTREE
        } yield {
          assert(l.id == u.id)
          assert(l.headers != u.headers)
          assert(l.lookupKeys != u.lookupKeys)
        }

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
