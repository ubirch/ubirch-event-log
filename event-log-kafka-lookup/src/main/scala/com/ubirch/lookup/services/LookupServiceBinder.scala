package com.ubirch.lookup.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.lookup.models.{ DefaultFinder, Finder }
import com.ubirch.lookup.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.lookup.services.kafka.consumer.DefaultRecordsManager
import com.ubirch.services._
import com.ubirch.services.cluster.{ ConnectionService, DefaultConnectionService }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.kafka.consumer.{ DefaultStringConsumer, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.util.cassandra.{ CQLSessionService, CassandraConfig, DefaultCQLSessionServiceProvider, DefaultCassandraConfigProvider }
import monix.execution.Scheduler

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
  def scheduler: ScopedBindingBuilder = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])
  def consumer: ScopedBindingBuilder = bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])

  //Cassandra Cluster
  def cassandraConfig: ScopedBindingBuilder = bind(classOf[CassandraConfig]).toProvider(classOf[DefaultCassandraConfigProvider])
  def cqlSessionService: ScopedBindingBuilder = bind(classOf[CQLSessionService]).toProvider(classOf[DefaultCQLSessionServiceProvider])
  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
  //Cassandra Cluster

  def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[DefaultFinder])

  def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultGremlinConnector])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    cassandraConfig
    cqlSessionService
    connectionService
    executionContext
    scheduler
    executorFamily
    consumer
    consumerRecordsManager
    producer
    gremlin
    finder
  }

}

object LookupServiceBinder {
  val modules: List[Module] = List(new LookupServiceBinder)
}
