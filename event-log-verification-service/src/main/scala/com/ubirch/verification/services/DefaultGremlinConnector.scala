package com.ubirch.verification.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import gremlin.scala.{ ScalaGraph, TraversalSource, _ }
import javax.inject._
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory
import org.janusgraph.core.JanusGraphFactory

import scala.concurrent.Future
import java.io.{ File, PrintWriter }

trait GremlinConnectorPaths {
  val JANUSGRAPH_PROPERTIES_PATH = "eventLog.gremlin.properties"
  val CASSANDRA_TRUSTSTORE_PATH = "eventLog.gremlin.truststore"
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

  val janusgraphPropertiesPath: Label = config.getString(JANUSGRAPH_PROPERTIES_PATH)

  val maybeTrustStore: Option[File] = createOptionTrustStore()

  val janusPropsUpdates: String = maybeTrustStore match {
    case Some(trustStore) => janusgraphPropertiesPath.replaceAll("/etc/opt/janusgraph/truststore.jks", trustStore.getAbsolutePath)
    case None => janusgraphPropertiesPath
  }

  val janusProps: File = createTempFile(janusPropsUpdates)

  implicit val graph: ScalaGraph = JanusGraphFactory.open(janusProps.getAbsolutePath)
  val b: Bindings = Bindings.instance
  val g: TraversalSource = graph.traversal

  def closeConnection(): Unit = {
    graph.close()
  }

  private def createOptionTrustStore(): Option[File] = {
    Option(config.getString(CASSANDRA_TRUSTSTORE_PATH)) match {
      case Some(trustStore) => if (!trustStore.isEmpty) {
        Option(createTempFile(contents = trustStore, prefix = Option("truststore"), suffix = Option("jks")))
      } else None
      case None => None
    }
  }

  private def createTempFile(contents: String, prefix: Option[String] = None, suffix: Option[String] = None): File = {
    val tempFi = File.createTempFile(
      prefix.getOrElse("prefix-"),
      suffix.getOrElse("-suffix")
    )
    tempFi.deleteOnExit()
    new PrintWriter(tempFi) {
      try {
        write(contents)
      } finally {
        close()
      }
    }
    logger.debug("temp file created at: " + tempFi.getAbsolutePath)
    tempFi
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down embedded Janusgraph: " + graph.toString)
    Future.successful(closeConnection())
  }

}

@Singleton
class DefaultTestingGremlinConnector() extends Gremlin {
  override implicit val graph: ScalaGraph = TinkerFactory.createModern().asScala()
  override val b: Bindings = Bindings.instance
  override val g: TraversalSource = graph.traversal
}

