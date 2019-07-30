package com.ubirch.discovery.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.discovery.services.kafka.consumer.DefaultExpressDiscovery
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.services._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

class DiscoveryServiceBinder
  extends AbstractModule
  with BasicServices //with ExecutionServices
  //with Kafka
  {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  //def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  //def consumerRecordsManager: ScopedBindingBuilder = bind(classOf[StringConsumerRecordsManager]).to(classOf[DefaultRecordsManager])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def expressKafka: ScopedBindingBuilder = bind(classOf[ExpressKafka[String, String, Unit]]).to(classOf[DefaultExpressDiscovery])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    executionContext
    expressKafka

    //consumer
    //consumerRecordsManager
    //producer

  }

}

object DiscoveryServiceBinder {
  val modules: List[Module] = List(new DiscoveryServiceBinder)
}
