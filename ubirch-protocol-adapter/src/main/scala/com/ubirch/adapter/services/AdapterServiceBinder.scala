package com.ubirch.adapter.services

import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.adapter.services.kafka.consumer.{ DefaultMessageEnvelopeConsumer, DefaultMessageEnvelopeManager, MessageEnvelopeConsumer, MessageEnvelopeConsumerRecordsManager }
import com.ubirch.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.producer.{ DefaultStringProducer, StringProducer }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

/**
  * Core Service Wiring
  */
class AdapterServiceBinder extends AbstractModule {

  override def configure(): Unit = {

    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
    bind(classOf[MessageEnvelopeConsumerRecordsManager]).to(classOf[DefaultMessageEnvelopeManager])

    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[MessageEnvelopeConsumer]).toProvider(classOf[DefaultMessageEnvelopeConsumer])
    bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])

  }

}

object AdapterServiceBinder {
  val modules: List[Module] = List(new AdapterServiceBinder)
}
