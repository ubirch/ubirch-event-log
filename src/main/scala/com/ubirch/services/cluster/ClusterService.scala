package com.ubirch.services.cluster

import com.datastax.driver.core.{ Cluster, PoolingOptions }
import com.typesafe.config.Config
import javax.inject._

import scala.collection.JavaConverters._

trait ClusterConfigs {
  val contactPoints: List[String]
  val port: Int
}

trait ClusterService extends ClusterConfigs {
  val poolingOptions: PoolingOptions
  val cluster: Cluster
}

@Singleton
class DefaultClusterService @Inject() (config: Config) extends ClusterService {

  val contactPoints: List[String] = config.getStringList("eventLog.cluster.contactPoints").asScala.toList
  val port: Int = config.getInt("eventLog.cluster.port")

  val poolingOptions = new PoolingOptions

  override val cluster = Cluster.builder
    .addContactPoints(contactPoints: _*)
    .withPort(port)
    .withPoolingOptions(poolingOptions)
    .build

}
