package com.ubirch.dispatcher.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.dispatcher.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.dispatcher.services.execution.DispatcherExecutionProvider
import com.ubirch.dispatcher.services.kafka.consumer.DefaultRecordsManager
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.consumer.{ DefaultStringConsumer, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter, DefaultMetricsLoggerCounter }

import scala.concurrent.ExecutionContext

class DispatcherServiceBinder
  extends AbstractModule
  with BasicServices
  with ExecutionServices
  with Kafka {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  def consumerRecordsManager: ScopedBindingBuilder = bind(classOf[StringConsumerRecordsManager]).to(classOf[DefaultRecordsManager])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def consumer: ScopedBindingBuilder = bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])
  def consumerRecordsManagerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultConsumerRecordsManagerCounter.name))
    .to(classOf[DefaultConsumerRecordsManagerCounter])
  def metricsLoggerCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultMetricsLoggerCounter.name))
    .to(classOf[DefaultMetricsLoggerCounter])
  def dispatchCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultDispatchingCounter.name))
    .to(classOf[DefaultDispatchingCounter])
  def dispatchingExecutionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext])
    .annotatedWith(Names.named("dispatcher"))
    .toProvider(classOf[DispatcherExecutionProvider])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    executionContext
    executorFamily
    consumer
    consumerRecordsManager
    producer
    consumerRecordsManagerCounter
    metricsLoggerCounter
    dispatchCounter
    dispatchingExecutionContext
  }

}

object DispatcherServiceBinder {
  val modules: List[Module] = List(new DispatcherServiceBinder)
}
