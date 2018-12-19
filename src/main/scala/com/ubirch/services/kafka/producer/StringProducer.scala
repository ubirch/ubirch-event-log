package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import javax.inject._
import org.apache.kafka.clients.producer.{ Producer, KafkaProducer ⇒ JKafkaProducer }
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }

import scala.collection.JavaConverters._
import scala.concurrent.Future

class StringProducer(props: Map[String, AnyRef]) extends KafkaProducerBase[String, String] {

  override val producer: Producer[String, String] = createConsumer(props)
  override val keySerializer: Serializer[String] = new StringSerializer()
  override val valueSerializer: Serializer[String] = new StringSerializer()

  private def createConsumer(props: Map[String, AnyRef]): Producer[String, String] = {
    keySerializer.configure(props.asJava, true)
    valueSerializer.configure(props.asJava, false)
    new JKafkaProducer(props.asJava, keySerializer, valueSerializer)
  }

}

class DefaultStringProducer @Inject() (
    config: Config,
    lifecycle: Lifecycle) extends Provider[StringProducer] {

  val configs = Configs()

  lazy val producer = new StringProducer(configs)

  override def get(): StringProducer = producer

  lifecycle.addStopHook { () ⇒
    Future.successful(producer.producer.close())
  }

}