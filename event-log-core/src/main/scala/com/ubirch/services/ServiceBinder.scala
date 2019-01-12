package com.ubirch.services

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import com.ubirch.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.consumer.{ DefaultStringConsumer, StringConsumer }
import com.ubirch.services.kafka.producer.{ DefaultStringProducer, StringProducer }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

class ServiceBinder extends AbstractModule {

  override def configure() = {

    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])

    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
    bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])

  }

}
