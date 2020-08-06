package com.ubirch.verification.service

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.niomon.healthcheck.HealthCheckServer
import com.ubirch.services._
import com.ubirch.services.cluster.{ ClusterService, ConnectionService, DefaultClusterService, DefaultConnectionService }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.verification.service.services.eventlog.{ CachedEventLogClient, EventLogClient, NewEventLogClient }
import com.ubirch.verification.service.services._
import com.ubirch.verification.service.util.udash.JettyServer

import scala.concurrent.ExecutionContext

class LookupServiceBinder extends AbstractModule with BasicServices with CassandraServices with ExecutionServices {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])

  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])

  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  //Cassandra Cluster
  def clusterService: ScopedBindingBuilder = bind(classOf[ClusterService]).to(classOf[DefaultClusterService])

  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])

  //Cassandra Cluster

  def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[DefaultFinder])

  def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultGremlinConnector])

  //CachedEventLogClient

  def newEventLogClient: ScopedBindingBuilder = bind(classOf[EventLogClient]).annotatedWith(Names.named("New")).to(classOf[NewEventLogClient])

  def cachedEventLogClient: ScopedBindingBuilder = bind(classOf[EventLogClient]).annotatedWith(Names.named("Cached")).to(classOf[CachedEventLogClient])

  def redisOpt: ScopedBindingBuilder = bind(classOf[RedisCache]).toProvider(classOf[RedisProvider])

  def healthCheck: ScopedBindingBuilder = bind(classOf[HealthCheckServer]).toProvider(classOf[HealthCheckProvider])

  def jettyServer: ScopedBindingBuilder = bind(classOf[JettyServer]).toProvider(classOf[JettyServerProvider])

  override def configure(): Unit = {
    lifecycle
    jvmHook
    config
    clusterService
    connectionService
    executionContext
    gremlin
    finder
    newEventLogClient
    cachedEventLogClient
    redisOpt
    healthCheck
    jettyServer
  }

}

object LookupServiceBinder {
  val modules: List[Module] = List(new LookupServiceBinder)
}
