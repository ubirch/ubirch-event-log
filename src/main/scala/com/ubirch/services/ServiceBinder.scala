package com.ubirch.services

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.{ DefaultStringConsumerUnit, StringConsumer }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

class ServiceBinder extends AbstractModule {

  override def configure() = {

    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    bind(classOf[StringConsumer[Unit]]).toProvider(classOf[DefaultStringConsumerUnit])

  }

}