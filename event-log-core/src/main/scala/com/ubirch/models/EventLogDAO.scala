package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents the queries linked to the EventLogRow case class and to the Events Table
  */
trait EventLogQueries extends TablePointer[EventLogRow] with CustomEncodings[EventLogRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[EventLogRow] = schemaMeta[EventLogRow]("events")

  def selectAllQ: db.Quoted[db.EntityQuery[EventLogRow]] = quote(query[EventLogRow])

  def insertQ(eventLogRow: EventLogRow): db.Quoted[db.Insert[EventLogRow]] = quote {
    query[EventLogRow].insert(lift(eventLogRow))
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

  def selectAll: Future[List[EventLogRow]] = run(selectAllQ)

  def insert(eventLogRow: EventLogRow): Future[RunActionResult] = run(insertQ(eventLogRow))

}

@Singleton
class EventsDAO @Inject() (events: Events, lookups: Lookups)(implicit ec: ExecutionContext) {

  def insert(eventLogRow: EventLogRow, lookupKeyRows: Seq[LookupKeyRow]): Future[Int] = {

    val fEventsRowResp = events.insert(eventLogRow).map(_ => 1)
    val LookupKeysFutureResp = lookupKeyRows.map(x => lookups.insert(x).map(_ => 1))

    val fLookupKeysResp = Future.sequence(fEventsRowResp +: LookupKeysFutureResp).map(_.sum)

    fLookupKeysResp

  }

}
