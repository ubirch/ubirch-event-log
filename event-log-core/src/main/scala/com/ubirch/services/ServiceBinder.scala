package com.ubirch.services

import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.process.{ DefaultExecutorFamily, ExecutorFamily }
import com.ubirch.services.cluster._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.consumer._
import com.ubirch.services.kafka.producer.{ DefaultStringProducer, StringProducer }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter, DefaultMetricsLoggerCounter }

import scala.concurrent.ExecutionContext

/**
  * Core Service Wiring
  */
class ServiceBinder extends AbstractModule {

  override def configure(): Unit = {

    //Basic Components
    bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
    bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
    bind(classOf[Config]).toProvider(classOf[ConfigProvider])
    bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
    //Basic Components

    //Cassandra Cluster
    bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
    bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
    //Cassandra Cluster

    //Counters
    bind(classOf[Counter])
      .annotatedWith(Names.named("DefaultConsumerRecordsManagerCounter"))
      .to(classOf[DefaultConsumerRecordsManagerCounter])
    bind(classOf[Counter])
      .annotatedWith(Names.named("DefaultMetricsLoggerCounter"))
      .to(classOf[DefaultMetricsLoggerCounter])
    //Counters

    //Execution
    bind(classOf[ExecutorFamily]).to(classOf[DefaultExecutorFamily])
    bind(classOf[StringConsumerRecordsManager]).to(classOf[DefaultConsumerRecordsManager])
    //Execution

    //Kafka
    bind(classOf[StringConsumer]).toProvider(classOf[DefaultStringConsumer])
    bind(classOf[StringProducer]).toProvider(classOf[DefaultStringProducer])
    //Kafka
  }

}

object ServiceBinder {
  val modules: List[Module] = List(new ServiceBinder)
}
