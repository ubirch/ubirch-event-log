package com.ubirch.discovery.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.discovery.services.kafka.consumer.DefaultExpressDiscovery
import com.ubirch.discovery.services.metrics.DefaultDeviceCounter
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.Counter

import scala.concurrent.ExecutionContext

class DiscoveryServiceBinder
  extends AbstractModule
  with BasicServices {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def expressKafka: ScopedBindingBuilder = bind(classOf[ExpressKafka[String, String, Unit]]).to(classOf[DefaultExpressDiscovery])

  def deviceCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultDeviceCounter.name))
    .to(classOf[DefaultDeviceCounter])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    executionContext
    expressKafka
    deviceCounter
  }

}

object DiscoveryServiceBinder {
  val modules: List[Module] = List(new DiscoveryServiceBinder)
}
