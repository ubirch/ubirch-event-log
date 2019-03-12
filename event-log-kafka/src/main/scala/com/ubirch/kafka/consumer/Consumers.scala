package com.ubirch.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

abstract class StringConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, String](ConsumerRunner.name)

object StringConsumer {

  def empty(implicit ec: ExecutionContext): StringConsumer = new StringConsumer() {}

  def fBased(f: ConsumerRecord[String, String] => Future[ProcessResult[String, String]])(implicit ec: ExecutionContext): StringConsumer = {
    new StringConsumer() {
      override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
        f(consumerRecord)
      }
    }
  }

  def controllerBased(controller: ConsumerRecordsController[String, String])(implicit ec: ExecutionContext): StringConsumer = {
    val consumer = empty
    consumer.setConsumerRecordsController(Some(controller))
    consumer
  }

}

abstract class BytesConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, Array[Byte]](ConsumerRunner.name)

object BytesConsumer {

  def empty(implicit ec: ExecutionContext): BytesConsumer = new BytesConsumer() {}

  def fBased(f: ConsumerRecord[String, Array[Byte]] => Future[ProcessResult[String, Array[Byte]]])(implicit ec: ExecutionContext): BytesConsumer = {
    new BytesConsumer() {
      override def process(consumerRecord: ConsumerRecord[String, Array[Byte]]): Future[ProcessResult[String, Array[Byte]]] = f(consumerRecord)
    }
  }

  def controllerBased(controller: ConsumerRecordsController[String, Array[Byte]])(implicit ec: ExecutionContext): BytesConsumer = {
    val consumer = empty
    consumer.setConsumerRecordsController(Some(controller))
    consumer
  }

}

