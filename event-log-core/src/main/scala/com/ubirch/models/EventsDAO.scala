package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents the queries linked to the EventLog case class and to the Events Table
  */
trait EventLogQueries extends TablePointer[EventLog] with CustomEncodings[EventLog] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[EventLog] = schemaMeta[EventLog]("events")

  def selectAllQ: db.Quoted[db.EntityQuery[EventLog]] = quote(query[EventLog])

  def insertQ(eventlog: EventLog): db.Quoted[db.Insert[EventLog]] = quote {
    query[EventLog].insert(lift(eventlog))
  }

}

/**
  * Represent the materialization of the queries. Queries here are actually executed and
  * a concrete connection context is injected.
  * @param connectionService Represents the db connection value that is injected.
  * @param ec Represent the execution context for asynchronous processing.
  */
@Singleton
class Events @Inject() (val connectionService: ConnectionService)(implicit ec: ExecutionContext) extends EventLogQueries {

  val db: CassandraAsyncContext[SnakeCase.type] = connectionService.context

  import db._

  //These actually run the queries.

  def selectAll: Future[List[EventLog]] = run(selectAllQ)

  def insert(eventLog: EventLog): Future[RunActionResult] = run(insertQ(eventLog))

}
