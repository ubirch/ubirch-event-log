package com.ubirch.verification.microservice.services


import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.Config
import com.ubirch.services._
import com.ubirch.services.cluster.{ClusterService, ConnectionService, DefaultClusterService, DefaultConnectionService}
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.{DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle}
import com.ubirch.verification.microservice.models.{DefaultFinder, Finder}

import scala.concurrent.ExecutionContext

class LookupServiceBinder
  extends AbstractModule
    with BasicServices
    with CassandraServices
    with ExecutionServices {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])

  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  //CachedEventLogClient

  //  def eventLogClient : ScopedBindingBuilder = bind(classOf[NewEventLogClient]).toProvider(classOf[ApiImpl])

  //Cassandra Cluster
  def clusterService: ScopedBindingBuilder = bind(classOf[ClusterService]).to(classOf[DefaultClusterService])

  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])

  //Cassandra Cluster

  def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[DefaultFinder])

  def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultGremlinConnector])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    clusterService
    connectionService
    executionContext
    gremlin
    finder
  }

}

object LookupServiceBinder {
  val modules: List[Module] = List(new LookupServiceBinder)
}
