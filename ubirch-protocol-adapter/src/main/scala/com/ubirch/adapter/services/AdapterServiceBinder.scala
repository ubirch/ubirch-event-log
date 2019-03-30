package com.ubirch.adapter.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.adapter.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.adapter.services.kafka.consumer.{ DefaultMessageEnvelopeConsumer, DefaultMessageEnvelopeManager, MessageEnvelopeConsumer, MessageEnvelopeConsumerRecordsManager }
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.producer.{ DefaultStringProducer, DefaultStringProducerConfigProperties, StringProducer }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

/**
  * Core Adapter Service Wiring
  */
class AdapterServiceBinder
  extends AbstractModule
  with BasicServices
  with ExecutionServices
  with Producer {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  def messageEnvelopeConsumerRecordsManager: ScopedBindingBuilder = bind(classOf[MessageEnvelopeConsumerRecordsManager]).to(classOf[DefaultMessageEnvelopeManager])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def messageEnvelopeConsumer: ScopedBindingBuilder = bind(classOf[MessageEnvelopeConsumer]).toProvider(classOf[DefaultMessageEnvelopeConsumer])
  def producerConfigProperties: ScopedBindingBuilder = bind(classOf[ConfigProperties])
    .annotatedWith(Names.named("DefaultStringProducerConfigProperties"))
    .toProvider(classOf[DefaultStringProducerConfigProperties])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    executorFamily
    messageEnvelopeConsumerRecordsManager
    config
    executionContext
    messageEnvelopeConsumer
    producerConfigProperties
    producer
  }

}

/**
  * Represents the companion object for the AdapterServiceBinder
  */
object AdapterServiceBinder {
  val modules: List[Module] = List(new AdapterServiceBinder)
}
