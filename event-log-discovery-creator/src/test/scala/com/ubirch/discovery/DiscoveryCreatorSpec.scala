package com.ubirch.discovery

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.discovery.models.Relation
import com.ubirch.discovery.process.RelationStrategyImpl
import com.ubirch.discovery.services.kafka.consumer.DefaultExpressDiscovery
import com.ubirch.discovery.services.metrics.DefaultDeviceCounter
import com.ubirch.discovery.util.{ DiscoveryJsonSupport, PMHelper }
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ Error, EventLog, LookupKey, Value, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.{ PortGiver, UUIDHelper }
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }
import org.json4s.JsonAST.JInt

class DiscoveryCreatorSpec extends TestBase with LazyLogging {

  "DiscoveryCreator" must {

    "create proper Relations for UPPs without chain" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))
      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }
        creator.start

        val range = 1 to 1
        val pms = range.map(_ => DiscoveryJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM2).get)
        val eventLogs = pms.map(EventLog(_).withNewId.withCurrentEventTime.withRandomNonce.withCategory(Values.UPP_CATEGORY).toJson)

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.forall(_.edge.label != Option(Values.UPP_CATEGORY)))
        assert(relations.forall(_.edge.label != Option(Values.DEVICE_CATEGORY)))
        assert(relations.exists(_.edge.label == Option(Values.UPP_CATEGORY + "->" + Values.DEVICE_CATEGORY)))
        assert(relations.exists(_.edge.properties.size == 1))

        assert(relations.nonEmpty)
        assert(relations.size == 1) // We expect to relations: UPP-DEVICE

      }

    }

    "create proper Relations for UPPs with chain" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))

      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }
        creator.start()

        val range = 1 to 1
        val pms = range.map(_ => DiscoveryJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
        val eventLogs = pms.map(EventLog(_).withNewId.withCurrentEventTime.withRandomNonce.withCategory(Values.UPP_CATEGORY).toJson)

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.forall(_.edge.label != Option(Values.UPP_CATEGORY)))
        assert(relations.forall(_.edge.label != Option(Values.DEVICE_CATEGORY)))
        assert(relations.exists(_.edge.label == Option(Values.UPP_CATEGORY + "->" + Values.DEVICE_CATEGORY)))
        assert(relations.exists(_.edge.properties.size == 1))
        assert(relations.forall(x => x.vFrom.properties.size == 4))
        assert(relations.forall(x => x.vTo.properties.size == 2))

        assert(relations.nonEmpty)
        assert(relations.size == 2) // We expect to relations: UPP-DEVICE, UPP-CHAIN

      }

    }

    "Send error to error topic" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val errorTopic_ : String = config.getString(ProducerConfPaths.ERROR_TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))

      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
          override def errorTopic: String = errorTopic_
        }
        creator.start

        publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), "Hola")

        Thread.sleep(5000)

        val errorAsJson = consumeFirstStringMessageFrom(errorTopic_)

        val el = DiscoveryJsonSupport.FromString[EventLog](errorAsJson).get
        val error = DiscoveryJsonSupport.FromJson[Error](el.event).get

        assert(el.isInstanceOf[EventLog])
        assert(error.isInstanceOf[Error])

      }

    }

    "create proper Relations for Slave Trees" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }

        creator.start()
        import LookupKey._

        val id = UUIDHelper.randomUUID
        val range = 1 to 1
        val eventLogs = range.map { x =>
          EventLog(JInt(x))
            .withNewId(id)
            .withCurrentEventTime
            .withRandomNonce
            .withCategory(Values.SLAVE_TREE_CATEGORY)
            .addLookupKeys(
              LookupKey(
                Values.SLAVE_TREE_ID, Values.SLAVE_TREE_CATEGORY,
                id.toString.asKeyWithLabel(Values.SLAVE_TREE_CATEGORY),
                Seq(Value(x.toString, Option(Values.SLAVE_TREE_CATEGORY),
                  Map(Values.SIGNATURE -> x.toString)))
              )
            )
            .toJson
        }

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.nonEmpty)
        assert(relations.size == range.size)
        assert(relations.exists(_.edge.properties.size == 1))


      }

    }

    "create proper Relations for Master Trees" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }

        creator.start
        import LookupKey._

        val id = UUIDHelper.randomUUID
        val range = 1 to 1
        val eventLogs = range.map { x =>
          EventLog(JInt(x))
            .withNewId(id)
            .withCurrentEventTime
            .withRandomNonce
            .withCategory(Values.MASTER_TREE_CATEGORY)
            .addLookupKeys(
              LookupKey(
                name = Values.MASTER_TREE_ID,
                category = Values.MASTER_TREE_CATEGORY,
                key = id.toString.asKeyWithLabel(Values.MASTER_TREE_CATEGORY),
                value = Seq(Value(x.toString, Option(Values.MASTER_TREE_CATEGORY),
                  Map(Values.SIGNATURE -> x.toString)))
              )
            )
            .toJson
        }

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.nonEmpty)
        assert(relations.size == range.size)
        assert(relations.exists(_.edge.properties.size == 1))


      }

    }

    "create proper Relations for Blockchain" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      val relationStrategy = new RelationStrategyImpl(new DefaultDeviceCounter(config))

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle, relationStrategy) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }

        creator.start
        import LookupKey._

        val txId = UUIDHelper.randomUUID
        val masterRootHash = UUIDHelper.randomUUID

        val id = UUIDHelper.randomUUID
        val range = 1 to 1
        val eventLogs = range.map { x =>
          EventLog("EventLogFromConsumerRecord", "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK", JInt(x))
            .withCustomerId(Values.UBIRCH)
            .withNewId(txId)
            .withLookupKeys(Seq(
              LookupKey(
                name = "ETHEREUM_TESTNET_RINKEBY_TESTNET_NETWORK",
                category = Values.PUBLIC_CHAIN_CATEGORY,
                key = txId.toString.asKey,
                value = Seq(masterRootHash.toString.asValue)
              ).categoryAsKeyLabel
                .addValueLabelForAll(Values.MASTER_TREE_CATEGORY)
            ))
            .toJson
        }

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.nonEmpty)
        assert(relations.size == range.size)
        assert(relations.exists(_.edge.properties.size == 1))


      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
