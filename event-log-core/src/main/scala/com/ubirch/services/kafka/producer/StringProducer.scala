package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import javax.inject._
import org.apache.kafka.clients.producer.{ Producer, KafkaProducer => JKafkaProducer }
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }

import scala.collection.JavaConverters._
import scala.concurrent.Future

class StringProducer(props: Map[String, AnyRef]) extends KafkaProducerBase[String, String] with LazyLogging {

  require(props.nonEmpty, "Can't be empty")

  val keySerializer: Serializer[String] = new StringSerializer()
  val valueSerializer: Serializer[String] = new StringSerializer()

  lazy val producer: Producer[String, String] = createConsumer(props)

  private def createConsumer(props: Map[String, AnyRef]): Producer[String, String] = {
    logger.debug("Creating Consumer ...")
    keySerializer.configure(props.asJava, true)
    valueSerializer.configure(props.asJava, false)
    new JKafkaProducer[String, String](props.asJava, keySerializer, valueSerializer)
  }

}

@Singleton
class DefaultStringProducer @Inject() (
    config: Config,
    lifecycle: Lifecycle
) extends Provider[StringProducer] with LazyLogging {

  import ConfPaths.Producer._

  val bootstrapServers: String = config.getStringList(BOOTSTRAP_SERVERS).asScala.mkString("")

  val configs = Configs(bootstrapServers)

  private lazy val producer = new StringProducer(configs)

  override def get(): StringProducer = producer

  lifecycle.addStopHook { () =>
    logger.info("Shutting down producer...")
    Future.successful(producer.producer.close())
  }

}
