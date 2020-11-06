package com.ubirch.verification

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import com.typesafe.config.Config
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.niomon.healthcheck.HealthCheckServer
import com.ubirch.services._
import com.ubirch.services.cluster.{ClusterService, ConnectionService, DefaultClusterService, DefaultConnectionService}
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.lifeCycle.{DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle}
import com.ubirch.verification.controllers.{Api, DefaultApi}
import com.ubirch.verification.services._
import com.ubirch.verification.services.eventlog.{CachedEventLogClient, DefaultEventLogClient, EventLogClient}
import com.ubirch.verification.services.janus.{DefaultGremlinConnector, Gremlin, GremlinFinder, GremlinFinderRemote}
import com.ubirch.verification.services.kafka.{AcctEventPublishing, DefaultAcctEventPublishing}
import com.ubirch.verification.util.udash.JettyServer

import scala.concurrent.ExecutionContext

class LookupServiceBinder extends AbstractModule with BasicServices with CassandraServices with ExecutionServices {

  def lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def jvmHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def executionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def clusterService: ScopedBindingBuilder = bind(classOf[ClusterService]).to(classOf[DefaultClusterService])
  def connectionService: ScopedBindingBuilder = bind(classOf[ConnectionService]).to(classOf[DefaultConnectionService])
  def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[DefaultFinder])
  def gremlinFinder: ScopedBindingBuilder = bind(classOf[GremlinFinder]).to(classOf[GremlinFinderRemote])
  def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultGremlinConnector])
  def eventLogClient: ScopedBindingBuilder = bind(classOf[EventLogClient]).to(classOf[DefaultEventLogClient])
  def cachedEventLogClient: ScopedBindingBuilder = bind(classOf[EventLogClient]).annotatedWith(Names.named("Cached")).to(classOf[CachedEventLogClient])
  def redisOpt: ScopedBindingBuilder = bind(classOf[RedisCache]).toProvider(classOf[RedisProvider])
  def healthCheck: ScopedBindingBuilder = bind(classOf[HealthCheckServer]).toProvider(classOf[HealthCheckProvider])
  def jettyServer: ScopedBindingBuilder = bind(classOf[JettyServer]).toProvider(classOf[JettyServerProvider])
  def tokenPubKey: ScopedBindingBuilder = bind(classOf[TokenPublicKey]).to(classOf[DefaultTokenPublicKey])
  def tokenVerification: ScopedBindingBuilder = bind(classOf[TokenVerification]).to(classOf[DefaultTokenVerification])
  def tokenCreation: ScopedBindingBuilder = bind(classOf[TokenCreation]).to(classOf[DefaultTokenCreation])
  def acctEventPublishing: ScopedBindingBuilder = bind(classOf[AcctEventPublishing]).to(classOf[DefaultAcctEventPublishing])
  def api: ScopedBindingBuilder = bind(classOf[Api]).to(classOf[DefaultApi])

  override def configure(): Unit = {
    gremlinFinder
    lifecycle
    jvmHook
    config
    clusterService
    connectionService
    executionContext
    gremlin
    finder
    eventLogClient
    cachedEventLogClient
    redisOpt
    healthCheck
    jettyServer
    tokenPubKey
    tokenVerification
    api
    acctEventPublishing
  }

}

object LookupServiceBinder {
  val modules: List[Module] = List(new LookupServiceBinder)
}
