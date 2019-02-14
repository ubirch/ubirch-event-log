package com.ubirch.services.kafka.consumer

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.execution.Execution
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ ShutdownableThread, UUIDHelper }
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.serialization.Deserializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.{ Future, blocking }
import scala.util.{ Failure, Success }

trait ProcessResult[K, V] {

  val id: UUID = UUIDHelper.randomUUID

  val consumerRecord: ConsumerRecord[K, V]

}

trait ConsumerRecordsController[K, V] {

  def process[A >: ProcessResult[K, V]](consumerRecord: ConsumerRecord[K, V]): Future[A]

}

abstract class ConsumerRunner[K, V](name: String)
  extends ShutdownableThread(name) with Execution with LazyLogging {

  private var consumer: Consumer[K, V] = _

  @BeanProperty var props: Map[String, AnyRef] = Map.empty

  @BeanProperty var topics: Set[String] = Set.empty

  @BeanProperty var pollTimeout: java.time.Duration = java.time.Duration.ofMillis(1000)

  @BeanProperty var pauseDuration: Int = 1000

  @BeanProperty var keyDeserializer: Option[Deserializer[K]] = None

  @BeanProperty var valueDeserializer: Option[Deserializer[V]] = None

  @BeanProperty var consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener] = None

  @BeanProperty var consumerRecordsController: Option[ConsumerRecordsController[K, V]] = None

  def process(consumerRecord: ConsumerRecord[K, V]): Future[ProcessResult[K, V]]

  def isValueEmpty(v: V): Boolean

  //This one is made public for testing purposes
  val isPaused: AtomicBoolean = new AtomicBoolean(false)

  override def execute(): Unit = {
    logger.info("Yey, Starting to Consume ...")
    try {
      createConsumer(getProps)
      subscribe(getTopics.toList, getConsumerRebalanceListenerBuilder)

      var failed = scala.collection.immutable.Vector.empty[Throwable]

      while (getRunning) {

        try {

          logger.debug("Polling..")
          val consumerRecords = consumer.poll(pollTimeout)
          val count = consumerRecords.count()

          if (!isPaused.get() && count > 0) {

            val batchCountDown = new CountDownLatch(count)

            val iterator = consumerRecords.iterator().asScala.filterNot(cr => isValueEmpty(cr.value()))
            iterator.foreach { cr =>
              val processing = process(cr)
              processing.onComplete {
                case Success(_) =>
                  batchCountDown.countDown()
                case Failure(e) =>
                  failed = failed :+ e
                  batchCountDown.countDown()
              }

            }

            //TODO: probably we should add a timeout
            batchCountDown.await()

          }

          val errors = failed
          if (errors.isEmpty) {
            //TODO: probably we should add a timeout
            consumer.commitSync()
          } else {
            failed = Vector()
            errors.foreach(x => throw x)
          }

        } catch {
          case _: NeedForPauseException =>
            val partitions = consumer.assignment()
            logger.warn("NeedForPauseException: {}", partitions.toString)
            consumer.pause(partitions)
            isPaused.set(true)
            Future {
              blocking {
                Thread.sleep(getPauseDuration)
                failed = failed :+ NeedForResumeException(s"Restarting after a $getPauseDuration millis Sleep...")
              }
            }
          case e: NeedForResumeException =>
            logger.warn("NeedForResumeException: {}", e.getMessage)
            val partitions = consumer.assignment()
            consumer.resume(partitions)
            isPaused.set(false)
          case e: Throwable =>
            logger.warn("Escalating  {}", e.getMessage)
            throw e

        }

      }

    } catch {
      case e: ConsumerCreationException =>
        logger.error("ConsumerCreationException: {}", e.getMessage)
        startGracefulShutdown()
      case e: EmptyTopicException =>
        logger.error("EmptyTopicException: {}", e.getMessage)
        startGracefulShutdown()
      case e: NeedForShutDownException =>
        logger.error("NeedForShutDownException: {}", e.getMessage)
        startGracefulShutdown()
      case e: Exception =>
        logger.error("Exception... {}", e.getMessage)
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
