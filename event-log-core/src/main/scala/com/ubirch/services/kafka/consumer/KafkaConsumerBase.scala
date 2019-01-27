package com.ubirch.services.kafka.consumer

import org.apache.kafka.clients.consumer.{ Consumer, KafkaConsumer => JKafkaConsumer }
import org.apache.kafka.common.serialization.Deserializer

import scala.collection.JavaConverters._

/**
  * Represent a basic definition of a Kafka Consumer
  * @tparam K Represents the Key value for the ConsumerRecord
  * @tparam V Represents the Value for the ConsumerRecord
  */
trait KafkaConsumerBase[K, V] {

  var consumer: Consumer[K, V] = _

  def topic: String

  val keyDeserializer: Deserializer[K]

  val valueDeserializer: Deserializer[V]

  def createConsumer(props: Map[String, AnyRef]): this.type = {
    keyDeserializer.configure(props.asJava, true)
    valueDeserializer.configure(props.asJava, false)
    consumer = new JKafkaConsumer[K, V](props.asJava, keyDeserializer, valueDeserializer)
    this
  }

  def subscribe(): this.type = {
    consumer.subscribe(List(topic).asJavaCollection)
    this
  }

  def isConsumerDefined: Boolean = Option(consumer).isDefined

  def isTopicDefined: Boolean = topic.nonEmpty
}
