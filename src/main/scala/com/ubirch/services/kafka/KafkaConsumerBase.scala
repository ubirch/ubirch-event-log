package com.ubirch.services.kafka

import org.apache.kafka.clients.consumer.{ ConsumerRecords, KafkaConsumer ⇒ JKafkaConsumer }
import org.apache.kafka.common.serialization.Deserializer

import scala.collection.JavaConverters._
import scala.util.Try

trait KafkaConsumerBase[K, V] {

  var consumer: JKafkaConsumer[K, V] = _

  private var _topic: String = ""

  def topic = _topic

  def withTopic(value: String): this.type = {
    _topic = value
    this
  }

  val keyDeserializer: Deserializer[K]

  val valueDeserializer: Deserializer[V]

  def createConsumer(props: Map[String, AnyRef]): JKafkaConsumer[K, V] = {
    keyDeserializer.configure(props.asJava, true)
    valueDeserializer.configure(props.asJava, false)
    consumer = new JKafkaConsumer[K, V](props.asJava, keyDeserializer, valueDeserializer)
    consumer
  }

  def subscribe(): this.type = {
    consumer.subscribe(List(topic).asJavaCollection)
    this
  }

  def pollRecords: Try[ConsumerRecords[K, V]] = {
    Try(consumer.poll(java.time.Duration.ofSeconds(1)))
  }

  def isConsumerDefined: Boolean = Option(consumer).isDefined

  def isTopicDefined: Boolean = topic.nonEmpty
}