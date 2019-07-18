package com.ubirch.encoder.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.encoder.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.encoder.services.kafka.consumer.{ DefaultEncoderConsumer, DefaultEncoderManager, EncoderConsumerRecordsManager }
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter, DefaultMetricsLoggerCounter }

import scala.concurrent.ExecutionContext

/**
  * Core Encoder Service Wiring
  */
class EncoderServiceBinder
  extends AbstractModule
  with BasicServices
  with CounterServices
  with ExecutionServices
  with Kafka {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  def consumerRecordsManager: ScopedBindingBuilder = bind(classOf[EncoderConsumerRecordsManager]).to(classOf[DefaultEncoderManager])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def consumer: ScopedBindingBuilder = bind(classOf[BytesConsumer]).toProvider(classOf[DefaultEncoderConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])
  def consumerRecordsManagerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultConsumerRecordsManagerCounter.name))
    .to(classOf[DefaultConsumerRecordsManagerCounter])
  def metricsLoggerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultMetricsLoggerCounter.name))
    .to(classOf[DefaultMetricsLoggerCounter])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    metricsLoggerCounter
    consumerRecordsManagerCounter
    executionContext
    executorFamily
    consumer
    consumerRecordsManager
    producer
  }

}

/**
  * Represents the companion object for the EncoderServiceBinder
  */
object EncoderServiceBinder {
  val modules: List[Module] = List(new EncoderServiceBinder)
}
