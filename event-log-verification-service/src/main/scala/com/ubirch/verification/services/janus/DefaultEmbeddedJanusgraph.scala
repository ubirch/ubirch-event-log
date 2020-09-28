package com.ubirch.verification.services.janus

import java.io.PrintWriter

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import gremlin.scala.{ ScalaGraph, TraversalSource, _ }
import javax.inject._
import org.apache.tinkerpop.gremlin.process.traversal.Bindings
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory
import org.janusgraph.core.JanusGraphFactory

import scala.concurrent.Future
import scala.io.Source

trait JanusConnectorPaths {
  val JANUSGRAPH_PROPERTIES_PATH = "eventLog.gremlin.properties"
  val CASSANDRA_TRUSTSTORE_PATH = "eventLog.gremlin.truststore"
}

@Singleton
class DefaultEmbeddedJanusgraph @Inject() (lifecycle: Lifecycle, config: Config)
  extends Gremlin
  with LazyLogging
  with JanusConnectorPaths {

  val janusgraphPropertiesPath: Label = config.getString(JANUSGRAPH_PROPERTIES_PATH)

  val maybeTrustStorePath: Option[String] = Option(config.getString(CASSANDRA_TRUSTSTORE_PATH)) match {
    case Some(trustStore) => if (!trustStore.isEmpty) Some(trustStore) else None
    case None => None
  }

  val janusProps: java.io.File = duplicateJanusPropsWithUpdatedTruststorePath(janusgraphPropertiesPath, maybeTrustStorePath)

  implicit val graph: ScalaGraph = JanusGraphFactory.open(janusProps.getAbsolutePath)
  val b: Bindings = Bindings.instance
  val g: TraversalSource = graph.traversal

  def closeConnection(): Unit = {
    graph.close()
  }

  private def duplicateJanusPropsWithUpdatedTruststorePath(janusPropsPath: String, maybeTrustStorePath: Option[String]): java.io.File = {
    val tempFi = java.io.File.createTempFile(
      "janus-",
      "-properties"
    )
    tempFi.deleteOnExit()
    val w = new PrintWriter(tempFi)
    val janusPropsFile = Source.fromFile(janusPropsPath)
    janusPropsFile.getLines
      .map { x => if (x.contains("proxy")) s"# $x" else x }
    janusPropsFile.close()
    w.close()
    logger.debug("temp file created at: " + tempFi.getAbsolutePath)
    tempFi
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down embedded Janusgraph: " + graph.toString)
    Future.successful(closeConnection())
  }

}

@Singleton
class DefaultTestingEmbeddedJanusgraph() extends Gremlin {
  override implicit val graph: ScalaGraph = TinkerFactory.createModern().asScala()
  override val b: Bindings = Bindings.instance
  override val g: TraversalSource = graph.traversal
}

