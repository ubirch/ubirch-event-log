package com.ubirch.kafka.producer

import java.util.concurrent.atomic.AtomicInteger

import com.ubirch.kafka.util.Exceptions.{ ProducerCreationException, ProducerNotStartedException }
import com.ubirch.kafka.util.{ Callback, Callback0, VersionedLazyLogging }
import org.apache.kafka.clients.producer.{ KafkaProducer, Producer, ProducerRecord, RecordMetadata, Callback => KafkaCallback }
import org.apache.kafka.common.serialization.Serializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

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

    if (getKeySerializer.isEmpty && getValueSerializer.isEmpty) {
      throw ProducerCreationException("No Serializers Found", "Please set the serializers for the key and value.")
    }

    if (getProps.isEmpty) {
      throw ProducerCreationException("No Properties Found", "Please, set the properties for the consumer creation.")
    }

    try {

      val ks = getKeySerializer.get
      val vs = getValueSerializer.get
      val propsAsJava = getProps.asJava

      ks.configure(propsAsJava, true)
      vs.configure(propsAsJava, false)

      producer = Option(new KafkaProducer[K, V](propsAsJava, ks, vs))
      producer

    } catch {
      case e: Exception =>
        throw ProducerCreationException("Error Creating Producer", e.getMessage)
    } finally {
      onPostProducerCreation.run(getProducerAsOpt)
    }

  }

  def getProducerAsOpt: Option[Producer[K, V]] = producer

  private def producerCallback(promise: Promise[RecordMetadata]): KafkaCallback = {
    producerCallback(result => promise.complete(result))
  }

  private def producerCallback(callback: Try[RecordMetadata] => Unit): KafkaCallback =
    new KafkaCallback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        val result =
          if (exception == null) Success(metadata)
          else Failure(exception)
        callback(result)
      }
    }

  def send(record: ProducerRecord[K, V]): Future[RecordMetadata] = {
    val promise = Promise[RecordMetadata]()
    try {
      getProducerOrCreate.send(record, producerCallback(promise))
    } catch {
      case NonFatal(e) => promise.failure(e)
    }

    promise.future
  }

  def sendWithCallback(record: ProducerRecord[K, V])(callback: Try[RecordMetadata] => Unit): Unit = {
    getProducerOrCreate.send(record, producerCallback(callback))
  }

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

