package com.ubirch.services.kafka.producer

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.URLsHelper
import javax.inject._
import org.apache.kafka.clients.producer.{ Producer, KafkaProducer => JKafkaProducer }
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }

import scala.collection.JavaConverters._
import scala.concurrent.Future

/**
  * Class that represents a String Producer Factory
  *
  * @param props Properties for initiating a Kafka Producer
  */
class StringProducer(props: Map[String, AnyRef]) extends KafkaProducerBase[String, String] with LazyLogging {

  require(props.nonEmpty, "Can't be empty")

  val isProducerCreated = new AtomicBoolean(false)

  val keySerializer: Serializer[String] = new StringSerializer()
  val valueSerializer: Serializer[String] = new StringSerializer()

  lazy val producer: Producer[String, String] = createConsumer(props)

  private def createConsumer(props: Map[String, AnyRef]): Producer[String, String] = {
    logger.debug("Creating Producer ...")
    keySerializer.configure(props.asJava, true)
    valueSerializer.configure(props.asJava, false)
    val producer = new JKafkaProducer[String, String](props.asJava, keySerializer, valueSerializer)
    isProducerCreated.set(true)
    producer
  }

}

/**
  * Class that represents a String Producer Factory with specific values from the config files
  * @param config Config Component for reading config properties
  * @param lifecycle LifeCycle Component Instance for adding the producer stop hook
  */
@Singleton
class DefaultStringProducer @Inject() (
    config: Config,
    lifecycle: Lifecycle
) extends Provider[StringProducer] with LazyLogging {

  import ConfPaths.Producer._

  val bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  val configs = Configs(bootstrapServers)

  private lazy val producer = new StringProducer(configs)

  override def get(): StringProducer = producer

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Producer...")

    if (get().isProducerCreated.get())
      Future.successful(get().producer.close())

    Future.unit
  }

}
