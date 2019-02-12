package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.process.{ Executor, WithConsumerRecordsExecutor }
import com.ubirch.util.ShutdownableThread
import org.apache.kafka.clients.consumer._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps

/**
  * Represents a definition of the kafka consumer.
  * It controls the way the messages are consumed and sent to the executors.
  * It controls the way errors are handled through the executors handlers
  *
  * @param name It is the name of the thread.
  * @tparam K  Represents the Key value for the ConsumerRecord
  * @tparam V  Represents the Value for the ConsumerRecord
  * @tparam R  Represents the Result type for the execution of the executors pipeline.
  * @tparam ER Represents the Exception Result type that is returned back
  *            when having handled the exceptions.
  */
abstract class AbstractConsumer[K, V, R, ER](val name: String)(implicit ec: ExecutionContext)
  extends ShutdownableThread(name)
  with KafkaConsumerBase[K, V]
  with WithConsumerRecordsExecutor[K, V, Future[R], Future[ER]]
  with ConfigBaseHelpers
  with LazyLogging {

  def isValueEmpty(v: V): Boolean

  def startPolling(): Unit = {
    logger.debug("Polling started")
    new Thread(this).start()
  }

  def async = true

  private def getIterator(consumerRecords: ConsumerRecords[K, V]) = {
    consumerRecords
      .iterator()
      .asScala
      .filterNot(cr => isValueEmpty(cr.value()))
      .map(cr => (cr, executor))
  }

  private def process(consumerRecord: ConsumerRecord[K, V], executor: Executor[ConsumerRecord[K, V], Future[R]]) = {

    val fRes: Future[Either[Option[ER], Option[R]]] = executor(consumerRecord)
      .map(x => Right(Some(x)))
      .recoverWith {
        case e: Exception =>
          executorExceptionHandler(e).map(x => Left(Some(x)))
        case e =>
          logger.error(e.getMessage)
          Future.successful(Left(None))
      }

    fRes.map { res =>
      res.fold({
        case Some(_) =>
        case None =>
          startGracefulShutdown()
      }, _ => {})
    }

  }

  //TODO: ADD CHECKS FOR WHEN THE CONSUMER AND TOPIC ARE USED.
  override def execute(): Unit = {

    try {

      createConsumer(props)
      //TODO: Add rebalancing
      subscribe()

      while (getRunning) {

        val polledResults = consumer.poll(java.time.Duration.ofSeconds(1))
        val mappedIterator = getIterator(polledResults)

        def continue = mappedIterator.hasNext && getRunning
        def next() = mappedIterator.next()

        //This is the actual traversal of the iterator
        while (continue) {

          val (consumerRecord, executor) = next()
          val _fRes = process(consumerRecord, executor)

          if (async)
            Await.result(_fRes, 2 seconds)
          else
            _fRes

        } //End While

        if (isAutoCommit) {
          commitSync()
        }

      }

    } finally {
      close()
    }

  }

}
