package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.ConsumerRunner

import scala.concurrent.Future

trait WithConsumerShutdownHook extends LazyLogging {
  def hookFunc(gracefulTimeout: Int, consumerRunner: => ConsumerRunner[_, _]): () => Future[Unit] = {
    () =>
      logger.info("Shutting down Consumer: " + consumerRunner.getName)
      Future.successful(consumerRunner.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }
}

object ConsumerShutdownHook extends WithConsumerShutdownHook
