package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.producer.ProducerRunner

import scala.concurrent.Future

trait WithProducerShutdownHook extends LazyLogging {

  def hookFunc(producerRunner: => ProducerRunner[_, _]): () => Future[Unit] = {
    () =>
      logger.info("Shutting down Producer...")
      producerRunner.getProducerAsOpt.map { prod =>
        Future.successful(prod.close())
      }.getOrElse {
        Future.unit
      }
  }

}

object ProducerShutdownHook extends WithProducerShutdownHook
