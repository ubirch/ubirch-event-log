package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.process.WithConsumerRecordsExecutor
import com.ubirch.util.ShutdownableThread

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try

/**
  * Represents a definition of the kafka consumer.
  * It controls the way the messages are consumed and sent to the executors.
  * It controls the way errors are handled through the executors handlers
  * @param name It is the name of the thread.
  * @tparam K Represents the Key value for the ConsumerRecord
  * @tparam V Represents the Value for the ConsumerRecord
  * @tparam R Represents the Result type for the execution of the executors pipeline.
  */
abstract class AbstractConsumer[K, V, R](val name: String)
  extends ShutdownableThread(name)
  with KafkaConsumerBase[K, V]
  with WithConsumerRecordsExecutor[K, V, Future[R]]
  with ConfigBaseHelpers
  with LazyLogging {

  def isValueEmpty(v: V): Boolean

  def startPolling(): Unit = {
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

          //This is the actual traversal of the iterator
          while (mappedIterator.hasNext) {

            val (cr, processedRecord) = mappedIterator.next()

            Try(Await.result(processedRecord(cr), 2 seconds))
              .recover {
                case e: Exception =>
                  executorExceptionHandler(e)
                case e =>
                  logger.error(e.getMessage)
                  startGracefulShutdown()
              }

          }

          if (isAutoCommit)
            consumer.commitSync()

        }
        consumer.close()
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
