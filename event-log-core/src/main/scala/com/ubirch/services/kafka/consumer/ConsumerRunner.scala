package com.ubirch.services.kafka.consumer

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.Exceptions._
import com.ubirch.util.ShutdownableThread
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.serialization.Deserializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success }

trait ProcessResult[K, V] {

  val consumerRecord: ConsumerRecord[K, V]

}

trait ConsumerRecordsController[K, V] {

  def process[A >: ProcessResult[K, V]](consumerRecord: ConsumerRecord[K, V]): Future[A]

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

  @BeanProperty var bufferSize: Int = 200

  def process(consumerRecord: ConsumerRecord[K, V]): Future[ProcessResult[K, V]]

  def isValueEmpty(v: V): Boolean

  def isPaused: AtomicBoolean = new AtomicBoolean(false)

  @volatile private var finishedProcs = scala.collection.immutable.Vector.empty[ProcessResult[K, V]]

  @volatile private var runningProcs = scala.collection.immutable.Vector.empty[Future[ProcessResult[K, V]]]

  private def checkErrors() = synchronized {

    val errors = scala.collection.mutable.ListBuffer.empty[Throwable]

    runningProcs
      .filter(x => x.isCompleted)
      .map(_.value)
      .filter(_.isDefined)
      .map(_.get)
      .foreach {
        case Success(value) => finishedProcs = finishedProcs :+ value
        case Failure(e) => errors :+ e
      }

    errors

  }

  private def prePoll(): Unit = synchronized {
    checkErrors().foreach(x => throw x)
  }

  override def execute(): Unit = {
    logger.info("Yey, Starting to Consume ...")
    try {
      createConsumer(getProps)
      subscribe(getTopics.toList, getConsumerRebalanceListenerBuilder)

      while (getRunning) {

        try {

          logger.debug("Polling..")

          /**
            * We check if the head has finished.
            * -If it finished successfully. It is simply removed.
            * --We add this to the commit buffer.
            * --We should also check if the commit buffer size is ready for commit.
            * -If it finished with a failure. The exception is thrown and it is removed.
            * We start polling.
            * We check if not paused
            * The records are converted to a nice iterator
            * The records and the iterator are sent to the process
            * The process future is put in a in-mem queue
            *
            */

          prePoll()

          val consumerRecords = consumer.poll(pollTimeout)

          if (!isPaused.get()) {
            val iterator = consumerRecords.iterator().asScala.filterNot(cr => isValueEmpty(cr.value()))
            iterator.foreach { cr =>
              val processing = process(cr)
              runningProcs = runningProcs :+ processing
            }

          }

        } catch {
          case _: NeedForPauseException =>

            val partitions = consumer.assignment()
            logger.warn("NeedForPauseException Requested on {}", partitions.toString)
            consumer.pause(partitions)
            isPaused.set(true)

          case e: NeedForResumeException =>
            logger.warn("NeedForResumeException Requested on {}", e.getMessage)
            val partitions = consumer.assignment()
            consumer.resume(partitions)
          case e: Throwable =>
            logger.warn("Escalating  {}", e.getMessage)
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
