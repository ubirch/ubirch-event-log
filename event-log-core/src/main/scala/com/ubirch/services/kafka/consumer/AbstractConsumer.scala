package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.process.WithConsumerRecordsExecutor
import com.ubirch.util.ShutdownableThread

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.Try

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

  override def execute(): Unit = {
    if (props.nonEmpty) {

      createConsumer(props)

      if (isConsumerDefined && isTopicDefined) {

        subscribe()

        while (getRunning) {

          val polledResults = consumer.poll(java.time.Duration.ofSeconds(1))

          val records = polledResults.iterator().asScala

          //This is only a description of the iterator
          val mappedIterator = records
            .filterNot(cr => isValueEmpty(cr.value()))
            .map(cr => (cr, executor))

          def continue = mappedIterator.hasNext
          def next() = mappedIterator.next()

          //This is the actual traversal of the iterator
          while (continue) {

            val (cr, processedRecord) = next()

            val fRes: Future[Either[Option[ER], Option[R]]] = processedRecord(cr)
              .map(x => Right(Some(x)))
              .recoverWith {
                case e: Exception => executorExceptionHandler(e).map(x => Left(Some(x)))
                case e =>
                  logger.error(e.getMessage)
                  Future.successful(Left(None))
              }

            Await.result(

              fRes.map { res =>
                res.fold({
                  case Some(_) =>
                  case None =>
                    startGracefulShutdown()
                }, _ => {})
              }, 2 seconds

            )

          } //End While

          if (isAutoCommit) {
            commitSync()
          }

        }

        close()

      } else {
        logger.error("consumer: {} and topic: {} ", isConsumerDefined, isTopicDefined)
        startGracefulShutdown()
      }
    } else {
      logger.error("props: {} ", props.toString())
      startGracefulShutdown()
    }

  }

}
