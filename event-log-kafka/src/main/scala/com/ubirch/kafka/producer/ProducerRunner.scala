package com.ubirch.kafka.producer

import java.util.concurrent.atomic.AtomicInteger

import com.ubirch.kafka.util.Exceptions.{ ProducerCreationException, ProducerNotStartedException }
import com.ubirch.kafka.util.{ Callback, Callback0, VersionedLazyLogging }
import org.apache.kafka.clients.producer.{ KafkaProducer, Producer }
import org.apache.kafka.common.serialization.Serializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
  * Represents a simple definition for a kafka producer
  * It supports callback on the producer creation event
  * @tparam K Represents the Key value
  * @tparam V Represents the Value
  */
abstract class ProducerRunner[K, V] extends VersionedLazyLogging {

  override val version: AtomicInteger = ProducerRunner.version

  private val onPreProducerCreation = new Callback0[Unit] {}

  private val onPostProducerCreation = new Callback[Option[Producer[K, V]], Unit] {}

  @BeanProperty var props: Map[String, AnyRef] = Map.empty

  @BeanProperty var keySerializer: Option[Serializer[K]] = None

  @BeanProperty var valueSerializer: Option[Serializer[V]] = None

  private var producer: Option[Producer[K, V]] = None

  def onPreProducerCreation(f: () => Unit): Unit = onPreProducerCreation.addCallback(f)

  def onPostProducerCreation(f: Option[Producer[K, V]] => Unit): Unit = onPostProducerCreation.addCallback(f)

  @throws[ProducerNotStartedException]
  def getProducer: Producer[K, V] = producer.getOrElse(throw ProducerNotStartedException("Producer has not been started."))

  def getProducerOrCreateAsOpt: Option[Producer[K, V]] = producer.orElse(start)

  @throws[ProducerCreationException]
  @throws[ProducerNotStartedException]
  def getProducerOrCreate: Producer[K, V] = producer.orElse(start).getOrElse(throw ProducerNotStartedException("Producer has not been started."))

  @throws(classOf[ProducerCreationException])
  def start: Option[Producer[K, V]] = {

    onPreProducerCreation.run()

    if (keySerializer.isEmpty && valueSerializer.isEmpty) {
      throw ProducerCreationException("No Serializers Found", "Please set the serializers for the key and value.")
    }

    if (props.isEmpty) {
      throw ProducerCreationException("No Properties Found", "Please, set the properties for the consumer creation.")
    }

    try {

      val ks = keySerializer.get
      val vs = valueSerializer.get

      ks.configure(props.asJava, true)
      vs.configure(props.asJava, false)

      producer = Option(new KafkaProducer[K, V](props.asJava, ks, vs))
      producer

    } catch {
      case e: Exception =>
        throw ProducerCreationException("Error Creating Producer", e.getMessage)
    } finally {
      onPostProducerCreation.run(getProducerAsOpt)
    }

  }

  def getProducerAsOpt: Option[Producer[K, V]] = producer

}

/**
  * Companion object for the ProducerRunner
  */
object ProducerRunner {

  val version: AtomicInteger = new AtomicInteger(0)

  def apply[K, V](props: Map[String, AnyRef], keySerializer: Option[Serializer[K]], valueSerializer: Option[Serializer[V]]): ProducerRunner[K, V] = {
    val pr = new ProducerRunner[K, V] {}
    pr.setProps(props)
    pr.setKeySerializer(keySerializer)
    pr.setValueSerializer(valueSerializer)
    pr
  }

}

