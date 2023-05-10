package com.ubirch.discovery

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.discovery.models.Relation
import com.ubirch.discovery.services.kafka.consumer.DefaultExpressDiscovery
import com.ubirch.discovery.util.{ DiscoveryJsonSupport, PMHelper }
import com.ubirch.kafka.util.PortGiver
import com.ubirch.models.{ Error, EventLog, LookupKey, Value, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.util.UUIDHelper
import io.prometheus.client.CollectorRegistry
import monix.execution.Scheduler
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.json4s.JsonAST.{ JInt, JString }

class DiscoveryCreatorSpec extends TestBase with LazyLogging {

  implicit val scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  "DiscoveryCreator" must {

    "create proper Relations for UPPs without chain" in {

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
        assert(relations.forall(x => x.vFrom.properties.size == 3))
        assert(relations.forall(x => x.vTo.properties.nonEmpty))

        assert(relations.nonEmpty)
        assert(relations.size == 2) // We expect to relations: UPP-DEVICE, UPP-CHAIN

      }

    }

    "Send error to error topic" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val config = new ConfigProvider get ()

      implicit val ec = new ExecutionProvider(config) get ()
      val lifecycle = new DefaultLifecycle

      val consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

      val errorTopic_ : String = config.getString(ProducerConfPaths.ERROR_TOPIC_PATH)

      withRunningKafka {
        val creator = new DefaultExpressDiscovery(config, lifecycle) {
          override def consumerBootstrapServers: String = bootstrapServers
          override def producerBootstrapServers: String = bootstrapServers
          override val errorTopic: String = errorTopic_
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
                Values.SLAVE_TREE_ID,
                Values.SLAVE_TREE_CATEGORY,
                id.toString.asKeyWithLabel(Values.SLAVE_TREE_CATEGORY),
                Seq(Value(x.toString, Option(Values.UPP_CATEGORY),
                  Map(Values.SIGNATURE -> x.toString)))
              ),
              LookupKey(
                Values.SLAVE_TREE_ID,
                Values.SLAVE_TREE_CATEGORY,
                id.toString.asKeyWithLabel(Values.SLAVE_TREE_CATEGORY),
                Seq(Value(x.toString, Option(Values.PUB_KEY_CATEGORY)))
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

    "create proper Relations for Public Keys" in {

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

        val hardwareId = UUIDHelper.randomUUID
        val publicKey = UUIDHelper.randomUUID

        val range = 1 to 1
        val eventLogs = range.map { _ =>
          EventLog("EventLogFromConsumerRecord", Values.PUB_KEY_CATEGORY, JString(publicKey.toString))
            .withCustomerId(hardwareId)
            .withNewId(publicKey)
            .withLookupKeys(Seq(
              LookupKey(
                name = Values.PUB_KEY_CATEGORY,
                category = Values.PUB_KEY_CATEGORY,
                key = publicKey.toString.asKey,
                value = Seq(hardwareId.toString.asValue)
              ).categoryAsKeyLabel
                .addValueLabelForAll(Values.PUB_KEY_CATEGORY)
            ))
            .withRandomNonce
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

    "creating relations based on hybrid tree" in {

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

        val eventLog = """{"headers":{"trace":["SLAVE"],"origin":["SLAVE_TREE"],"dispatcher":["tags-exclude:aggregation"]},"id":"b2742d1a7148a41a4eb13d077a9218a3d528bbe7658f6cc3f1d945efa3e448a0468b630f76771ef9d569f93f38682fb692d9b5c22a660b4e7b8aecabd8557160","customer_id":"ubirch","service_class":"ubirchChainerSlave","category":"SLAVE_TREE","event":{"root":"b2742d1a7148a41a4eb13d077a9218a3d528bbe7658f6cc3f1d945efa3e448a0468b630f76771ef9d569f93f38682fb692d9b5c22a660b4e7b8aecabd8557160","leaves":["40a30c824fbd65afc2611141a5ebb6c49209fa9c18a45a9ff1e3ba0d5632530fd35ba1c58a0df52579a4d394e5d2f969376aae65e3ff8dbd24f2cf84e33a613b","f4381e1444087e994dcd4bc58bff04c302f8da16d5d5267d397750516be347b40aedbf94ced0224c48c3a7fa68435eac705ad6cdecbc5f2572f180991572750f","f95118d03e847393fe760460f42fe82b9d710b86e56c74b3d2347408f7368a2446d041a0f2e9c8ce862cd73bdcbbe7591d41e937f95d3bf5b0cb18d30b3f27ca","79c2a88ce2eaf09bd8a131f4d477429f03817ea89f1a5b722e6ee3d348e0432621c2538870b58ea2fad3acd0c2cfdb874c4f9da1e1eb842edc7c64a70504cc3c","7c0c651bdb303ead98acf4cbd33133cf233317c912efcfe56d269e03a11a690a9603dcee2e87de147edc450ad7f9297d9002847c6e20d1cd910f53f6fad93520","6574c7ce9f8aee594c8be1d63534383d6b355ac0c447c36421fcc330f9c5b87fd336a9ed9a64124615a27d8aa5f7a6b617f42d858d53f35a868a65d9a3f2b3cb","6fe50e7ce5f1b65a1733903d63a654b604ee22e16769b10d25dbcb8511a316bc848a51fa6e166ac38c35ee7a568da89dac184c7d692bc471f7c83ffad2ffa7a1","2fdc55b30a2854449326b42ca42f902eb9c1b1a4db62fe6deec515040c1f56fe6b12132641dc9ef029fe7b626833e5d03147b33bc73f2c12c64fba51305f5efc"]},"event_time":"2020-06-19T10:45:54.281Z","signature":"","nonce":"31633732653566652D363264352D346434372D383063622D373039336135613132313465","lookup_keys":[{"name":"slave-tree-link-id","category":"SLAVE_TREE","key":{"name":"b2742d1a7148a41a4eb13d077a9218a3d528bbe7658f6cc3f1d945efa3e448a0468b630f76771ef9d569f93f38682fb692d9b5c22a660b4e7b8aecabd8557160","label":"SLAVE_TREE"},"value":[{"name":"40a30c824fbd65afc2611141a5ebb6c49209fa9c18a45a9ff1e3ba0d5632530fd35ba1c58a0df52579a4d394e5d2f969376aae65e3ff8dbd24f2cf84e33a613b","label":"SLAVE_TREE","extra":{}}]},{"name":"slave-tree-id","category":"SLAVE_TREE","key":{"name":"b2742d1a7148a41a4eb13d077a9218a3d528bbe7658f6cc3f1d945efa3e448a0468b630f76771ef9d569f93f38682fb692d9b5c22a660b4e7b8aecabd8557160","label":"SLAVE_TREE"},"value":[{"name":"XBvCdGGIdHialsJXA3cdyedVbJpS39MLPT1GP8HnVIA0FP8OXIDIOD7jEJfz5DD5+4K6hw+G0ySVgYpOjsSKvQ==","label":"UPP","extra":{"signature":"PNwmprbidcPfF+IfxSyAR6P1Dsk1dhdKsnh/ytC46vgPFDZOURH0UMnGJaNOVAzzVp/qrXd3hOoTeGzFYEJrCg=="}},{"name":"kvdvWQ7NOT+HLDcrFqP/UZWy4QVcjfmmkfyzAgg8bitaK/FbHUPeqEji0UmCSlyPk5+4mEaEiZAHnJKOyqUZxA==","label":"PUB_KEY","extra":{}},{"name":"H0ukCj3jYDHVpnZ/of3/hBR3G8L3PicEbPFBT2fBiG0=","label":"UPP","extra":{"signature":"CBq43RleP2VLEaG0BMR+6GFwwHOBc+IUpXd9jKagNVZgtlcw/c9HwZfz0rUylR8jAUFF/qCcbKlLF3wKmbz+xw=="}},{"name":"7vaVGzVEXJTJdm+j02YNsbFLaL1pamWTVx0R0DdlPJA=","label":"UPP","extra":{"signature":"DJDdu6quhX0ocKzua0eUfO7qAHjeChIGo5/bnj3rgL7YKeh0YxwKQDhI6Y1oSw24DkSe1PZ6Yko4pKxdVx0jsw=="}},{"name":"hGwAx8JxPDoXXDT3EDqtBd7ssjaJly9FUEqZDj3lx+I=","label":"UPP","extra":{"signature":"w3yj1L91XjqgDPOMsXG/3UiB/BO6g2mldtIT35LrN5xvkMh4jL7LcoEBRne1tXyA8Q39/8GjFN3Q4BU7ojT+MA=="}},{"name":"5fYYEfhqA84Ruqji/AG8GvM6jaS5P3jx7AotS9RZXtE=","label":"UPP","extra":{"signature":"KqG0QUifJwa6FvEJG8uQhLzbRJWIS9EONaucH7em7AhdeHrHpJMHP6lDdj+DWiEHFbUm7iPtrUFs6Z1x1LLeEw=="}},{"name":"LtN1/K9nAFOHY5Zu+mNUmMJH1jfyWRsgKV9sZJsei7U=","label":"UPP","extra":{"signature":"ja3fijfc8c9ylYq4vA9gus5P7LuPf3LiEJdOOdcdQoEMNr0nl0NrZre1/Cb4vQ4VNdRzluGfZxwSRv7wkhKBzQ=="}}]}]}"""

        publishStringMessageToKafka(consumerTopics.toList.headOption.getOrElse(""), eventLog)

        Thread.sleep(5000)

        val relationsAsJson = consumeFirstStringMessageFrom(producerTopic)

        val relations = DiscoveryJsonSupport.FromString[Seq[Relation]](relationsAsJson).get

        assert(relations.nonEmpty)
        assert(relations.size == 8)
        assert(relations.exists(_.edge.properties.size == 1))

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
