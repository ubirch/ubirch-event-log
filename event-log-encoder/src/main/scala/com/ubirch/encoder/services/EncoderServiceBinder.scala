package com.ubirch.encoder.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.encoder.services.execution.EncodingExecutionProvider
import com.ubirch.encoder.services.kafka.consumer.DefaultEncoderConsumer
import com.ubirch.encoder.services.metrics.DefaultEncodingsCounter
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }

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
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[EncodingExecutionProvider])
  def consumer: ScopedBindingBuilder = bind(classOf[BytesConsumer]).toProvider(classOf[DefaultEncoderConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])
  def successCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultSuccessCounter.name))
    .to(classOf[DefaultSuccessCounter])
  def failureCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultFailureCounter.name))
    .to(classOf[DefaultFailureCounter])
  def encodingsCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultEncodingsCounter.name))
    .to(classOf[DefaultEncodingsCounter])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    successCounter
    failureCounter
    encodingsCounter
    executionContext
    consumer
    //consumerRecordsManager
    producer
  }

}

/**
  * Represents the companion object for the EncoderServiceBinder
  */
object EncoderServiceBinder {
  val modules: List[Module] = List(new EncoderServiceBinder)
}
