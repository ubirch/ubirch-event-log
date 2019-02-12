package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ FailureFuture, ShutdownableThread }
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.serialization.Deserializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

abstract class ConsumerRecordsController[K, V] extends FailureFuture {

  def process(consumerRecords: ConsumerRecords[K, V], iterator: Iterator[ConsumerRecord[K, V]]): Unit

}

abstract class ConsumerRunner[K, V](name: String)
  extends ShutdownableThread(name) with LazyLogging {

  private var consumer: Consumer[K, V] = _

  @BeanProperty var props: Map[String, AnyRef] = Map.empty

  @BeanProperty var topics: Set[String] = Set.empty

  @BeanProperty var pollTimeout: java.time.Duration = java.time.Duration.ofMillis(1000)

  @BeanProperty var keyDeserializer: Option[Deserializer[K]] = None

  @BeanProperty var valueDeserializer: Option[Deserializer[V]] = None

  @BeanProperty var consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener] = None

  @BeanProperty var consumerRecordsController: Option[ConsumerRecordsController[K, V]] = None

  def process(consumerRecords: ConsumerRecords[K, V], iterator: Iterator[ConsumerRecord[K, V]]): Unit

  def isValueEmpty(v: V): Boolean

  override def execute(): Unit = {
    logger.info("Yey, Starting to Consume ...")
    try {
      createConsumer(getProps)
      subscribe(getTopics.toList, getConsumerRebalanceListenerBuilder)

      while (getRunning) {

        try {
          val consumerRecords = consumer.poll(pollTimeout)
          val iterator = consumerRecords.iterator().asScala.filterNot(cr => isValueEmpty(cr.value()))
          process(consumerRecords, iterator)

        } catch {
          case e: NeedForPauseException =>
            logger.warn(e.getMessage)
            val partitions = consumer.assignment()
            consumer.pause(partitions)
          case e: NeedForResumeException =>
            logger.info(e.getMessage)
            val partitions = consumer.assignment()
            consumer.resume(partitions)
          case e: Throwable =>
            throw e

        }

      }

    } catch {
      case e: ConsumerCreationException =>
        logger.error(e.getMessage)
        startGracefulShutdown()
      case e: EmptyTopicException =>
        logger.error(e.getMessage)
        startGracefulShutdown()
      case e: NeedForShutDownException =>
        logger.error(e.getMessage)
        startGracefulShutdown()
      case e: Exception =>
        logger.error("Seems like this exception is not handled, shutting down... {}", e.getMessage)
        startGracefulShutdown()
    } finally {
      if (consumer != null) consumer.close()
    }
  }

  @throws(classOf[ConsumerCreationException])
  def createConsumer(props: Map[String, AnyRef]): Unit = {
    if (keyDeserializer.isEmpty && valueDeserializer.isEmpty) {
      throw ConsumerCreationException("No Serializers Found", "Please set the serializers for the key and value.")
    }

    if (props.isEmpty) {
      throw ConsumerCreationException("No Properties Found", "Please, set the properties for the consumer creation.")
    }

    try {
      val kd = keyDeserializer.get
      val vd = valueDeserializer.get

      kd.configure(props.asJava, true)
      vd.configure(props.asJava, false)

      consumer = new KafkaConsumer[K, V](props.asJava, kd, vd)

    } catch {
      case e: Exception =>
        throw ConsumerCreationException("Error Creating Consumer", e.getMessage)
    }
  }

  @throws(classOf[EmptyTopicException])
  def subscribe(topics: List[String], consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener]): Unit = {
    if (topics.nonEmpty) {
      val topicsAsJava = topics.asJavaCollection
      consumerRebalanceListenerBuilder match {
        case Some(crl) => consumer.subscribe(topicsAsJava, crl(consumer))
        case None => consumer.subscribe(topicsAsJava)
      }

    } else {
      throw EmptyTopicException("Topic cannot be empty.")
    }
  }

  def startPolling(): Unit = start()

}
