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

trait KafkaConsumerBase[K, V] {

  var consumer: JKafkaConsumer[K, V] = _

  val topic: String

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

}

trait WithConsumerRecordsExecutor[K, V, R] {

  val executor: Executor[ConsumerRecords[K, V], R]

}

abstract class AbstractConsumer[K, V, R](name: String)
    extends ShutdownableThread(name)
    with KafkaConsumerBase[K, V]
    with WithConsumerRecordsExecutor[K, V, Future[R]]
    with LazyLogging {

  val props: Map[String, AnyRef]

  def startPolling(): Unit = {
    new Thread(this).start()
  }

  def doWork(): Try[R] = {
    pollRecords
      .map(executor)
      .map(Await.result(_, 2 seconds))
  }

  override def execute(): Unit = {
    createConsumer(props)
    if (Option(consumer).isDefined) {
      logger.debug("Starting work ...")
      subscribe()
      while (getRunning) {
        doWork().recover {
          case e: Exception ⇒
            e.printStackTrace()
            logger.error("Got an ERROR processing records.")
            if (!NonFatal(e)) {
              startGracefulShutdown()
            }
        }
      }
      consumer.close()
    } else {
      logger.debug("No consumer created ...")
    }
  }

}
