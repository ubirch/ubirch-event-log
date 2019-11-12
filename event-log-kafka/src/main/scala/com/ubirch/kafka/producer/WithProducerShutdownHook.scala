package com.ubirch.kafka.producer

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait WithProducerShutdownHook extends LazyLogging {
  def hookFunc(producerRunner: => ProducerRunner[_, _]): () => Future[Unit] = {
    () =>
      val timeout = 5 seconds

      logger.info(s"Shutting down Producer[timeout=$timeout]...")
      Future.successful(producerRunner.close(timeout))
  }
}

object ProducerShutdownHook extends WithProducerShutdownHook
