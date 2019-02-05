package com.ubirch.services.cluster

import java.net.InetSocketAddress

import com.datastax.driver.core.{ Cluster, PoolingOptions }
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.util.Exceptions.NoContactPointsException
import com.ubirch.util.URLsHelper
import javax.inject._

/**
  * Component that contains configuration-related values.
  */
trait ClusterConfigs {

  val contactPoints: List[InetSocketAddress]

  def buildContactPointsFromString(contactPoints: String): List[InetSocketAddress] =
    URLsHelper.inetSocketAddressesString(contactPoints)

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

  val contactPoints: List[InetSocketAddress] = buildContactPointsFromString(config.getString(CONTACT_POINTS))
  val username: String = config.getString(USERNAME)
  val password: String = config.getString(PASSWORD)

  val poolingOptions = new PoolingOptions

  override val cluster = Cluster.builder
    .addContactPointsWithPorts(contactPoints: _*)
    .withPoolingOptions(poolingOptions)
    .withCredentials(username, password)
    .build

}
