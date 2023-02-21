package com.ubirch.services.cluster

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.CassandraClusterConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.cassandra.CQLSessionService
import io.getquill.{ CassandraAsyncContext, NamingStrategy, SnakeCase }

import javax.inject._
import scala.concurrent.Future

/**
  * Component that represents a Connection Service.
  * A Connection Service represents the connection established to the
  * Cassandra database.
  */
trait ConnectionServiceBase[N <: NamingStrategy] {
  val context: CassandraAsyncContext[N]
}

/**
  * Component that represents a Connection Service whose Naming Strategy
  * is ShakeCase.
  */

trait ConnectionService extends ConnectionServiceBase[SnakeCase]

/**
  * Default Implementation of the Connection Service Component.
  * It add shutdown hooks.
  * @param lifecycle Lifecycle injected component that allows for shutdown hooks.
  */

@Singleton
class DefaultConnectionService @Inject() (cqlSessionService: CQLSessionService, lifecycle: Lifecycle)
  extends ConnectionService with CassandraClusterConfPaths with LazyLogging {

  override val context = new CassandraAsyncContext(
    SnakeCase,
    cqlSessionService.cqlSession,
    cqlSessionService.preparedStatementCacheSize
  )

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Connection Service")
    Future.successful(context.close())
  }

}
