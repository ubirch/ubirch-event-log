package com.ubirch.lookup.services

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.{ DefaultLifecycle, Lifecycle }
import gremlin.scala.{ ScalaGraph, TraversalSource, _ }

import javax.inject._
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.driver.ser.Serializers
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory

import java.util
import scala.concurrent.Future

trait GremlinConnectorPaths {
  val HOSTS = "eventLog.gremlin.hosts"
  val PORTS = "eventLog.gremlin.port"
  val CLASS_NAME = "eventLog.gremlin.serializer.className"
  val MAX_WAIT_FOR_CONNECTION = "eventLog.gremlin.connectionPool.maxWaitForConnection"
  val RECONNECT_INTERVAL = "eventLog.gremlin.connectionPool.reconnectInterval"
  val IO_REGISTRIES = "eventLog.gremlin.serializer.config.ioRegistries"
}

trait Gremlin {
  implicit def graph: ScalaGraph
  def b: Bindings
  def g: TraversalSource
}

@Singleton
class DefaultGremlinConnector @Inject() (lifecycle: Lifecycle, config: Config)
  extends Gremlin
  with LazyLogging
  with GremlinConnectorPaths {

  lazy val cluster: Cluster = buildCluster(config)

  implicit val graph: ScalaGraph = EmptyGraph.instance.asScala.configure(_.withRemote(DriverRemoteConnection.using(cluster)))
  val b: Bindings = Bindings.instance
  val g: TraversalSource = graph.traversal

  logger.info("[property] getNioPoolSize=" + cluster.getNioPoolSize)
  logger.info("[property] getWorkerPoolSize=" + cluster.getWorkerPoolSize)

  def buildCluster(config: Config): Cluster = {
    val cluster = Cluster.build()
    val hosts: List[String] = config.getString(HOSTS)
      .split(",")
      .toList
      .map(_.trim)
      .filter(_.nonEmpty)

    cluster.addContactPoints(hosts: _*)
      .port(config.getInt(PORTS))
    val maxWaitForConnection = config.getInt(MAX_WAIT_FOR_CONNECTION)
    if (maxWaitForConnection > 0) cluster.maxWaitForConnection(maxWaitForConnection)

    val reconnectInterval = config.getInt(RECONNECT_INTERVAL)
    if (reconnectInterval > 0) cluster.reconnectInterval(reconnectInterval)

    val conf = new util.HashMap[String, AnyRef]()
    conf.put("ioRegistries", config.getAnyRef(IO_REGISTRIES).asInstanceOf[java.util.ArrayList[String]])
    val serializer = Serializers.GRAPHBINARY_V1D0.simpleInstance()
    serializer.configure(conf, null)

    cluster.serializer(serializer).create()
  }

  def closeConnection(): Unit = {
    cluster.close()
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down connection with Janus: " + cluster.toString)
    Future.successful(closeConnection())
  }

}

@Singleton
class DefaultTestingGremlinConnector() extends Gremlin {
  override implicit val graph: ScalaGraph = TinkerFactory.createModern().asScala()
  override val b: Bindings = Bindings.instance
  override val g: TraversalSource = graph.traversal
}
