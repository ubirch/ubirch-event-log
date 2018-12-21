package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.execution.WithConsumerRecordsExecutor
import com.ubirch.util.ShutdownableThread

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try

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
            .filterNot(cr ⇒ isValueEmpty(cr.value()))
            .map(cr ⇒ (cr, executor))

          //This is the actual traversal of the iterator
          while (mappedIterator.hasNext) {

            val (cr, processedRecord) = mappedIterator.next()

            Try(Await.result(processedRecord(cr), 2 seconds))
              .recover {
                case e: Exception ⇒ executorExceptionHandler(e)
                case _ ⇒
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
