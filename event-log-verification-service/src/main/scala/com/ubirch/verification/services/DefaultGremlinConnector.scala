package com.ubirch.verification.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import gremlin.scala.{ ScalaGraph, TraversalSource, _ }
import javax.inject._
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.tinkerpop.gremlin.driver.Cluster
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.structure.util.empty.EmptyGraph
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory

import scala.concurrent.Future

trait GremlinConnectorPaths {
  val HOSTS_PATH = "eventLog.gremlin.hosts"
  val PORTS_PATH = "eventLog.gremlin.port"
  val CLASS_NAME_PATH = "eventLog.gremlin.serializer.className"
  val MAX_WAIT_FOR_CONNECTION_PATH = "eventLog.gremlin.connectionPool.maxWaitForConnection"
  val RECONNECT_INTERVAL_PATH = "eventLog.gremlin.connectionPool.reconnectInterval"
  val IO_REGISTRIES_PATH = "eventLog.gremlin.serializer.config.ioRegistries"
  val NIO_POOL_SIZE_PATH = "eventLog.gremlin.nioPoolSize"
  val WORKER_POOL_SIZE_PATH = "eventLog.gremlin.workerPoolSize"
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

  lazy val cluster: Cluster = Cluster.open(buildProperties(config))

  implicit val graph: ScalaGraph = EmptyGraph.instance.asScala.configure(_.withRemote(DriverRemoteConnection.using(cluster)))
  val b: Bindings = Bindings.instance
  val g: TraversalSource = graph.traversal

  logger.info("[property] getNioPoolSize=" + cluster.getNioPoolSize)
  logger.info("[property] getWorkerPoolSize=" + cluster.getWorkerPoolSize)

  def buildProperties(config: Config): PropertiesConfiguration = {
    val conf = new PropertiesConfiguration()
    conf.addProperty("hosts", config.getString(HOSTS_PATH))
    conf.addProperty("port", config.getString(PORTS_PATH))
    conf.addProperty("serializer.className", config.getString(CLASS_NAME_PATH))
    conf.addProperty("connectionPool.maxWaitForConnection", config.getString(MAX_WAIT_FOR_CONNECTION_PATH))
    conf.addProperty("connectionPool.reconnectInterval", config.getString(RECONNECT_INTERVAL_PATH))
    // no idea why the following line needs to be duplicated. Doesn't work without
    // cf https://stackoverflow.com/questions/45673861/how-can-i-remotely-connect-to-a-janusgraph-server first answer, second comment ¯\_ツ_/¯
    conf.addProperty("serializer.config.ioRegistries", config.getAnyRef(IO_REGISTRIES_PATH).asInstanceOf[java.util.ArrayList[String]])
    conf.addProperty("serializer.config.ioRegistries", config.getStringList(IO_REGISTRIES_PATH))

    val nioPoolSize = config.getInt(NIO_POOL_SIZE_PATH)
    if (nioPoolSize > 0) conf.addProperty("nioPoolSize", nioPoolSize)

    val workerPoolSize = config.getInt(WORKER_POOL_SIZE_PATH)
    if (workerPoolSize > 0) conf.addProperty("workerPoolSize", workerPoolSize)

    conf
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

