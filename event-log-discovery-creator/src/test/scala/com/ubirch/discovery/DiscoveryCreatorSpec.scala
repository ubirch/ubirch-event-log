package com.ubirch.discovery

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.discovery.models.Relation
import com.ubirch.discovery.services.kafka.consumer.DefaultExpressDiscovery
import com.ubirch.discovery.util.{ DiscoveryJsonSupport, PMHelper }
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ EventLog, LookupKey, Value, Values, Error }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.{ PortGiver, UUIDHelper }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.{ Deserializer, Serializer }
import org.json4s.JsonAST.JInt

class DiscoveryCreatorSpec extends TestBase with LazyLogging {

  "DiscoveryCreator" must {

    "create proper Relations for UPPs" in {

      implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
      implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
        }
        creator.start

        val range = 1 to 2
        val pms = range.map(_ => DiscoveryJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
        val eventLogs = pms.map(EventLog(_).withNewId.withCurrentEventTime.withRandomNonce.withCategory(Values.UPP_CATEGORY).toJson)

        eventLogs.foreach(x => publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), x))

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.nonEmpty)
        assert(relations.size == range.size)

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

      val producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

      def errorTopic_ : String = config.getString(ProducerConfPaths.ERROR_TOPIC_PATH)

      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle) {
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

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle) {
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

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle) {
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
                Values.SLAVE_TREE_ID, Values.MASTER_TREE_CATEGORY,
                id.toString.asKeyWithLabel(Values.MASTER_TREE_CATEGORY),
                Seq(Value(x.toString, Option(Values.MASTER_TREE_CATEGORY),
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

      withRunningKafka {

        val creator = new DefaultExpressDiscovery(config, lifecycle) {
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

      }

    }

  }

}