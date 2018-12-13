package com.ubirch.services.cluster

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import io.getquill.{ CassandraAsyncContext, NamingStrategy, SnakeCase }
import javax.inject._

import scala.concurrent.Future

trait ConnectionServiceConfig {
  val keyspace: String
  val preparedStatementCacheSize: Int
}

trait ConnectionServiceBase extends ConnectionServiceConfig {
  type N <: NamingStrategy
  val context: CassandraAsyncContext[N]
}

trait ConnectionService extends ConnectionServiceBase {
  type N = SnakeCase.type
}

@Singleton
class DefaultConnectionService @Inject() (clusterService: ClusterService, config: Config, lifecycle: Lifecycle)
    extends ConnectionService with LazyLogging {

  val keyspace: String = config.getString(ConfPaths.KEYSPACE)
  val preparedStatementCacheSize: Int = config.getInt(ConfPaths.PREPARED_STATEMENT_CACHE_SIZE)

  private def createContext() = new CassandraAsyncContext(
    SnakeCase,
    clusterService.cluster,
    keyspace,
    preparedStatementCacheSize)

  override val context = {
    val conn = createContext()
    logger.info("Connected to keyspace: " + keyspace)
    conn
  }

  lifecycle.addStopHook { () ⇒
    Future.successful(context.close())
  }

}