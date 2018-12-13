package com.ubirch.services.kafka

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.ShutdownableThread
import org.apache.kafka.clients.consumer.{ ConsumerRecords, KafkaConsumer ⇒ JKafkaConsumer }
import org.apache.kafka.common.serialization.Deserializer

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

abstract class AbstractConsumer[K, V, R](name: String)
    extends ShutdownableThread(name)
    with LazyLogging {

  val topic: String

  val props: Map[String, AnyRef]

  val keyDeserializer: Deserializer[K]

  val valueDeserializer: Deserializer[V]

  val maybeExecutor: Option[Executor[ConsumerRecords[K, V], Future[R]]]

  implicit def ec: ExecutionContext

  var consumer: JKafkaConsumer[K, V] = _

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

  def startPolling(): Unit = {
    new Thread(this).start()
  }

  def doWork(): Option[R] = {
    val records = pollRecords
    records match {
      case Success(crs) ⇒
        maybeExecutor match {
          case Some(executor) ⇒
            logger.debug("Doing work...")
            Option(Await.result(executor(crs), 2 second))
          case None ⇒
            logger.warn("No Executor Found. Shutting down")
            startGracefulShutdown()
            None

        }
      case Failure(NonFatal(e)) ⇒
        e.printStackTrace()
        logger.error("Got this error:  {} ", e.getMessage)
        None
      case Failure(e) ⇒
        e.printStackTrace()
        logger.error("Got FATAL error:  {} ", e.getMessage)
        startGracefulShutdown()
        None
    }
  }

  override def execute(): Unit = {
    createConsumer(props)
    if (Option(consumer).isDefined) {
      logger.debug("Starting work ...")
      subscribe()
      while (getRunning) {
        doWork()
      }
      consumer.close()
    } else {
      logger.debug("No consumer created ...")
    }
  }

}
