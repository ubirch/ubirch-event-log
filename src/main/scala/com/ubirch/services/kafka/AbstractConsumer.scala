package com.ubirch.services.kafka

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.ShutdownableThread

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal

abstract class AbstractConsumer[K, V, R](name: String)
    extends ShutdownableThread(name)
    with KafkaConsumerBase[K, V]
    with WithConsumerRecordsExecutor[K, V, Future[R]]
    with LazyLogging {

  private var _props: Map[String, AnyRef] = Map.empty

  def props = _props

  def withProps(ps: Map[String, AnyRef]): this.type = {
    _props = ps
    this
  }

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
    if (isConsumerDefined && isTopicDefined) {
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
      logger.error("consumer: {} and topic: {} ", isConsumerDefined, isTopicDefined)
      startGracefulShutdown()
    }
  }

}
