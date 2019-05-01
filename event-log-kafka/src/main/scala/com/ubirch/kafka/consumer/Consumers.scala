package com.ubirch.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a Basic String Consumer
  * @param ec Represents the execution context.
  */
abstract class StringConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, String](ConsumerRunner.name)

/**
  * Contains useful helpers for a StringConsumer
  */
object StringConsumer {

  def empty(implicit ec: ExecutionContext): StringConsumer = new StringConsumer() {}

  def emptyWithMetrics(implicit ec: ExecutionContext): StringConsumer = new StringConsumer() with WithMetrics[String, String]

  def fBased(f: Vector[ConsumerRecord[String, String]] => Future[ProcessResult[String, String]])(implicit ec: ExecutionContext): StringConsumer = {
    new StringConsumer() {
      override def process(consumerRecords: Vector[ConsumerRecord[String, String]]): Future[ProcessResult[String, String]] = {
        f(consumerRecords)
      }
    }
  }

  def controllerBased(controller: ConsumerRecordsController[String, String])(implicit ec: ExecutionContext): StringConsumer = {
    val consumer = empty
    consumer.setConsumerRecordsController(Some(controller))
    consumer
  }

}

/**
  * Represents a Basic Bytes Consumer
  * @param ec Represents the execution context.
  */
abstract class BytesConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, Array[Byte]](ConsumerRunner.name)

/**
  * Contains useful helpers for a  BytesConsumer
  */
object BytesConsumer {

  def empty(implicit ec: ExecutionContext): BytesConsumer = new BytesConsumer() {}

  def emptyWithMetrics(implicit ec: ExecutionContext): BytesConsumer = new BytesConsumer() with WithMetrics[String, Array[Byte]]

  def fBased(f: Vector[ConsumerRecord[String, Array[Byte]]] => Future[ProcessResult[String, Array[Byte]]])(implicit ec: ExecutionContext): BytesConsumer = {
    new BytesConsumer() {
      override def process(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]]): Future[ProcessResult[String, Array[Byte]]] = {
        f(consumerRecords)
      }
    }
  }

  def controllerBased(controller: ConsumerRecordsController[String, Array[Byte]])(implicit ec: ExecutionContext): BytesConsumer = {
    val consumer = empty
    consumer.setConsumerRecordsController(Some(controller))
    consumer
  }

}

