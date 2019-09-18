package com.ubirch.chainer.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.chainer.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.chainer.services.kafka.consumer.DefaultChainerManager
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.consumer.{ DefaultStringConsumer, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter, DefaultMetricsLoggerCounter }
import com.ubirch.services.{ BasicServices, ExecutionServices, Kafka }

import scala.concurrent.ExecutionContext

class ChainerServiceBinder extends AbstractModule
  with BasicServices
  with ExecutionServices
  with Kafka {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  def consumerRecordsManager: ScopedBindingBuilder = bind(classOf[StringConsumerRecordsManager]).to(classOf[DefaultChainerManager])

  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def consumer: ScopedBindingBuilder = bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])

  def instantMonitor: ScopedBindingBuilder = bind(classOf[InstantMonitor]).to(classOf[AtomicInstantMonitor])

  def consumerRecordsManagerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultConsumerRecordsManagerCounter.name))
    .to(classOf[DefaultConsumerRecordsManagerCounter])
  def metricsLoggerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultMetricsLoggerCounter.name))
    .to(classOf[DefaultMetricsLoggerCounter])
  def treesCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultTreeCounter.name))
    .to(classOf[DefaultTreeCounter])

  def leavesCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultLeavesCounter.name))
    .to(classOf[DefaultLeavesCounter])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    executionContext
    executorFamily
    consumer
    consumerRecordsManager
    producer
    instantMonitor
    consumerRecordsManagerCounter
    metricsLoggerCounter
    treesCounter
    leavesCounter
  }

}

object ChainerServiceBinder {
  val modules: List[Module] = List(new ChainerServiceBinder)

}
