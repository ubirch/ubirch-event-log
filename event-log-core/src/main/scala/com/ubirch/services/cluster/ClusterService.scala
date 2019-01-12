package com.ubirch.services.cluster

import com.datastax.driver.core.{ Cluster, PoolingOptions }
import com.typesafe.config.Config
import com.ubirch.ConfPaths
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
