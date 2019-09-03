package com.ubirch.services.cluster

import java.net.InetSocketAddress

import com.datastax.driver.core._
import com.datastax.driver.core.policies.RoundRobinPolicy
import com.typesafe.config.Config
import com.ubirch.ConfPaths.CassandraClusterConfPaths
import com.ubirch.util.Exceptions.InvalidConsistencyLevel
import com.ubirch.util.URLsHelper
import javax.inject._

/**
  * Component that contains configuration-related values.
  */
trait ClusterConfigs {

  val contactPoints: List[InetSocketAddress]

  def buildContactPointsFromString(contactPoints: String): List[InetSocketAddress] = {
    URLsHelper.inetSocketAddressesString(contactPoints)
  }

  val maybeConsistencyLevel: Option[ConsistencyLevel]

  val maybeSerialConsistencyLevel: Option[ConsistencyLevel]

  def checkConsistencyLevel(consistencyLevel: String): Option[ConsistencyLevel] = try {
    if (consistencyLevel.isEmpty)
      None
    else {
      Option(ConsistencyLevel.valueOf(consistencyLevel))
    }
  } catch {
    case e: Exception =>
      throw InvalidConsistencyLevel("Invalid Consistency Level: " + e.getMessage)
  }

}

/**
  * Component that defines a Cassandra Cluster.
  */

trait ClusterService extends ClusterConfigs {
  val poolingOptions: PoolingOptions
  val queryOptions: QueryOptions
  val cluster: Cluster
}

/**
  * Default implementation of the Cluster Service Component.
  * @param config Represent an injected config object.
  */

@Singleton
class DefaultClusterService @Inject() (config: Config) extends ClusterService with CassandraClusterConfPaths {

  val contactPoints: List[InetSocketAddress] = buildContactPointsFromString(config.getString(CONTACT_POINTS))
  val maybeConsistencyLevel: Option[ConsistencyLevel] = checkConsistencyLevel(config.getString(CONSISTENCY_LEVEL))
  val maybeSerialConsistencyLevel: Option[ConsistencyLevel] = checkConsistencyLevel(config.getString(SERIAL_CONSISTENCY_LEVEL))
  val withSSL: Boolean = config.getBoolean(WITH_SSL)
  val username: String = config.getString(USERNAME)
  val password: String = config.getString(PASSWORD)

  val poolingOptions = new PoolingOptions().setMaxQueueSize(1024)
    .setMaxRequestsPerConnection(HostDistance.LOCAL, 32768)
    .setMaxRequestsPerConnection(HostDistance.REMOTE, 2000)

  val queryOptions = new QueryOptions

  maybeConsistencyLevel.foreach { cl =>
    queryOptions.setConsistencyLevel(cl)
  }

  maybeSerialConsistencyLevel.foreach { cl =>
    queryOptions.setSerialConsistencyLevel(cl)
  }

  override val cluster: Cluster = {
    val builder = Cluster.builder
      .addContactPointsWithPorts(contactPoints: _*)
      .withLoadBalancingPolicy(new RoundRobinPolicy())
      .withCompression(ProtocolOptions.Compression.LZ4)
      .withPoolingOptions(poolingOptions)
      .withCredentials(username, password)
      .withQueryOptions(queryOptions)
      .withProtocolVersion(ProtocolVersion.V3)
      .withClusterName("event-log")

    if (withSSL) {
      builder.withSSL()
    }

    builder.build()

  }

}
