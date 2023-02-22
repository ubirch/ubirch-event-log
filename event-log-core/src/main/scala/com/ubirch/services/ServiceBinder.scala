package com.ubirch.services

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.consumer._
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.cassandra.{ CQLSessionService, CassandraConfig, DefaultCQLSessionServiceProvider, DefaultCassandraConfigProvider }

import scala.concurrent.ExecutionContext

trait BasicServices {
  def lifecycle: ScopedBindingBuilder
  def jvmHook: ScopedBindingBuilder
  def config: ScopedBindingBuilder
  def executionContext: ScopedBindingBuilder
}

trait CassandraServices {
  def cassandraConfig: ScopedBindingBuilder
  def cqlSessionService: ScopedBindingBuilder
  def connectionService: ScopedBindingBuilder
}

trait CounterServices {
  def successCounter: ScopedBindingBuilder
  def failureCounter: ScopedBindingBuilder
}

trait ExecutionServices {
  //def executorFamily: ScopedBindingBuilder
}

trait Consumer {
  def consumer: ScopedBindingBuilder
  //def consumerRecordsManager: ScopedBindingBuilder
}

trait Producer {
  def producer: ScopedBindingBuilder
  // def producerConfigProperties: ScopedBindingBuilder
}

trait Kafka extends Consumer with Producer

/**
  * Core Service Wiring
  */
class ServiceBinder
  extends AbstractModule
  with BasicServices
  with CassandraServices
  with CounterServices
  with ExecutionServices
  with Kafka {

  //Basic Components
  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  //Basic Components

  //Cassandra Cluster
  def cassandraConfig: ScopedBindingBuilder = bind(classOf[CassandraConfig]).toProvider(classOf[DefaultCassandraConfigProvider])
  def cqlSessionService: ScopedBindingBuilder = bind(classOf[CQLSessionService]).toProvider(classOf[DefaultCQLSessionServiceProvider])
  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
  //Cassandra Cluster

  //Counters
  def successCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultSuccessCounter.name))
    .to(classOf[DefaultSuccessCounter])
  def failureCounter: ScopedBindingBuilder = bind(classOf[Counter])
    .annotatedWith(Names.named(DefaultFailureCounter.name))
    .to(classOf[DefaultFailureCounter])
  //Counters

  //Execution
  def executorFamily: ScopedBindingBuilder = bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
  def consumerRecordsManager: ScopedBindingBuilder = bind(classOf[StringConsumerRecordsManager]).to(classOf[DefaultConsumerRecordsManager])
  //Execution

  //Kafka
  def consumer: ScopedBindingBuilder = bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
  def producer: ScopedBindingBuilder = bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])
  //Kafka

  override def configure(): Unit = {

    //Basic Components
    lifecycle
    jvmHook
    config
    executionContext
    //Basic Components

    //Cassandra Cluster
    cassandraConfig
    cqlSessionService
    connectionService
    //Cassandra Cluster

    //Counters
    successCounter
    failureCounter
    //Counters

    //Execution
    executorFamily
    //Execution

    //Kafka
    consumer
    consumerRecordsManager
    producer
    //Kafka
  }

}

object ServiceBinder {
  val modules: List[Module] = List(new ServiceBinder)
}
