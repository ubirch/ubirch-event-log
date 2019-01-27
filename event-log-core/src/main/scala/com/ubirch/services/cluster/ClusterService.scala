package com.ubirch.services.cluster

import com.datastax.driver.core.{ Cluster, PoolingOptions }
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import javax.inject._

import scala.collection.JavaConverters._

/**
  * Component that contains configuration-related values.
  */
trait ClusterConfigs {
  val contactPoints: List[String]
  val port: Int
}

/**
  * Component that defines a Cassandra Cluster.
  */

trait ClusterService extends ClusterConfigs {
  val poolingOptions: PoolingOptions
  val cluster: Cluster
}

/**
  * Default implementation of the Cluster Service Component.
  * @param config Represent an injected config object.
  */

@Singleton
class DefaultClusterService @Inject() (config: Config) extends ClusterService {

  import ConfPaths.CassandraCluster._

  val contactPoints: List[String] = config.getStringList(CONTACT_POINTS).asScala.toList
  val port: Int = config.getInt(PORT)
  val username: String = config.getString(USERNAME)
  val password: String = config.getString(PASSWORD)

  val poolingOptions = new PoolingOptions

  override val cluster = Cluster.builder
    .addContactPoints(contactPoints: _*)
    .withPort(port)
    .withPoolingOptions(poolingOptions)
    .withCredentials(username, password)
    .build

}
