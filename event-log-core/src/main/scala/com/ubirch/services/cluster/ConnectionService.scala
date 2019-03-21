package com.ubirch.services.cluster

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.CassandraClusterConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Exceptions.NoKeyspaceException
import io.getquill.{ CassandraAsyncContext, NamingStrategy, SnakeCase }
import javax.inject._

import scala.concurrent.Future

/**
  * Component that represent the basic configuration for the ConnectionService Component.
  */
trait ConnectionServiceConfig {
  val keyspace: String
  val preparedStatementCacheSize: Int
}

/**
  * Component that represents a Connection Service.
  * A Connection Service represents the connection established to the
  * Cassandra database.
  */
trait ConnectionServiceBase extends ConnectionServiceConfig {
  type N <: NamingStrategy
  val context: CassandraAsyncContext[N]
}

/**
  * Component that represents a Connection Service whose Naming Strategy
  * is ShakeCase.
  */

trait ConnectionService extends ConnectionServiceBase {
  type N = SnakeCase.type
}

/**
  * Default Implementation of the Connection Service Component.
  * It add shutdown hooks.
  * @param clusterService Cluster Service Component.
  * @param config Configuration injected component.
  * @param lifecycle Lifecycle injected component that allows for shutdown hooks.
  */

@Singleton
class DefaultConnectionService @Inject() (clusterService: ClusterService, config: Config, lifecycle: Lifecycle)
  extends ConnectionService with CassandraClusterConfPaths with LazyLogging {

  val keyspace: String = config.getString(KEYSPACE)
  val preparedStatementCacheSize: Int = config.getInt(PREPARED_STATEMENT_CACHE_SIZE)

  if (keyspace.isEmpty) {
    throw NoKeyspaceException("Keyspace must be provided.")
  }

  private def createContext() = new CassandraAsyncContext(
    SnakeCase,
    clusterService.cluster,
    keyspace,
    preparedStatementCacheSize
  )

  override val context = {
    val conn = createContext()
    logger.info("Connected to keyspace: " + keyspace)
    conn
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Connection Service")
    Future.successful(context.close())
  }

}
