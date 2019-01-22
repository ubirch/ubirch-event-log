package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

trait EventLogQueries extends TablePointer[EventLog] with CustomEncodings[EventLog] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[EventLog] = schemaMeta[EventLog]("events")

  def selectAllQ: db.Quoted[db.EntityQuery[EventLog]] = quote(query[EventLog])

  def insertQ(eventlog: EventLog): db.Quoted[db.Insert[EventLog]] = quote {
    query[EventLog].insert(lift(eventlog))
  }

}

@Singleton
class Events @Inject() (val connectionService: ConnectionService)(implicit ec: ExecutionContext) extends EventLogQueries {

  val db: CassandraAsyncContext[SnakeCase.type] = connectionService.context

  import db._

  //These actually run the queries.

  def selectAll: Future[List[EventLog]] = run(selectAllQ)

  def insert(eventLog: EventLog): Future[RunActionResult] = run(insertQ(eventLog))

}
