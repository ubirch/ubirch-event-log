package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.process.{ Executor, ExecutorFamily }
import com.ubirch.services.kafka.producer.Reporter
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class UbirchProtocolAdapterConsumerRecordsManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  override type A = AdapterPipeData

  override def executor: Executor[ConsumerRecord[String, String], Future[AdapterPipeData]] = ???

  override def executorExceptionHandler: PartialFunction[Throwable, Future[AdapterPipeData]] = ???

  override def process(consumerRecord: ConsumerRecord[String, String]): Future[AdapterPipeData] = ???
}
