package com.ubirch.services.kafka

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.ShutdownableThread
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal

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

  def startPolling(): Unit = {
    new Thread(this).start()
  }

  def doWork(): Try[R] = {
    pollRecords
      .map(executor)
      .map(Await.result(_, 2 seconds))
  }

  override def execute(): Unit = {
    if (props.nonEmpty) {
      createConsumer(props)
      if (isConsumerDefined && isTopicDefined) {
        subscribe()
        while (getRunning) {
          doWork()
            .map { _ ⇒
              if (isAutoCommit)
                consumer.commitSync()
            }
            .recover {
              case e: Exception ⇒
                logger.error("Got an ERROR processing records: " + e.getMessage)
                if (!NonFatal(e)) {
                  startGracefulShutdown()
                }
            }
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
