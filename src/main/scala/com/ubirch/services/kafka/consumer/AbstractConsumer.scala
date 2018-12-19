package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Error
import com.ubirch.util.Exceptions.{ EmptyValueException, ParsingIntoEventLogException, StoringIntoEventLogException }
import com.ubirch.util.ShutdownableThread
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try

trait ConfigBaseHelpers {

  private var _props: Map[String, AnyRef] = Map.empty

  def props = _props

  def withProps(ps: Map[String, AnyRef]): this.type = {
    _props = ps
    this
  }

  private var _topic: String = ""

  def topic = _topic

  def withTopic(value: String): this.type = {
    _topic = value
    this
  }

  def isAutoCommit: Boolean = props
    .filterKeys(x ⇒ ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG == x)
    .exists {
      case (_, b) ⇒
        b.toString.toBoolean
    }

}

abstract class AbstractConsumer[K, V, R](name: String)
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

            import reporter.Types._

            Try(Await.result(processedRecord(cr), 2 seconds))
              .recover {
                case e: EmptyValueException ⇒
                  reporter.report(Error(id = UUID.randomUUID(), message = e.getMessage, exceptionName = e.name))
                case e: ParsingIntoEventLogException ⇒
                  reporter.report(Error(id = UUID.randomUUID(), message = e.getMessage, exceptionName = e.name, value = e.value))
                case e: StoringIntoEventLogException ⇒
                  reporter.report(Error(id = e.eventLog.event.id, message = e.getMessage, exceptionName = e.name, value = e.eventLog.toString))
                case e: Exception ⇒
                  reporter.report(Error(id = UUID.randomUUID(), message = e.getMessage, exceptionName = e.getClass.getCanonicalName))

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
