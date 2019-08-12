package com.ubirch.lookup.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.lookup.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.lookup.services.kafka.consumer.DefaultRecordsManager
import com.ubirch.services._
import com.ubirch.services.cluster.{ ClusterService, ConnectionService, DefaultClusterService, DefaultConnectionService }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, LoggerExecutionProvider }
import com.ubirch.services.kafka.consumer.{ DefaultStringConsumer, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }

import scala.concurrent.ExecutionContext

class LookupServiceBinder
  extends AbstractModule
  with BasicServices
  with CassandraServices
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

  //Cassandra Cluster
  def clusterService: ScopedBindingBuilder = bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
  //Cassandra Cluster
  def loggerExecutionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext])
    .annotatedWith(Names.named("logger"))
    .toProvider(classOf[LoggerExecutionProvider])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    clusterService
    connectionService
    executionContext
    executorFamily
    consumer
    consumerRecordsManager
    producer
    loggerExecutionContext

  }

}

object LookupServiceBinder {
  val modules: List[Module] = List(new LookupServiceBinder)
}
