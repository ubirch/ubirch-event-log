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

  def byIdAndCatQ(id: String, category: String) = quote {
    query[EventLogRow].filter(x => x.id == lift(id) && x.category == lift(category)).map(x => x)
  }

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

  def byIdAndCat(id: String, category: String): Future[List[EventLogRow]] = run(byIdAndCatQ(id, category))

  def insert(eventLogRow: EventLogRow): Future[Unit] = run(insertQ(eventLogRow))

}

@Singleton
class EventsDAO @Inject() (val events: Events, val lookups: Lookups)(implicit ec: ExecutionContext) {

  def insertFromEventLog(eventLog: EventLog): Future[Int] = {
    insert(EventLogRow.fromEventLog(eventLog), eventLog.lookupKeys.flatMap(x => LookupKeyRow.fromLookUpKey(x)))
  }

  def insert(eventLogRow: EventLogRow, lookupKeyRows: Seq[LookupKeyRow]): Future[Int] = {

    val fEventsRowResp = events.insert(eventLogRow).map(_ => 1)
    val lookupKeysFutureResp = lookupKeyRows.map(x => lookups.insert(x).map(_ => 1))
    val fLookupKeysResp = Future.sequence(fEventsRowResp +: lookupKeysFutureResp).map(_.sum)

    fLookupKeysResp

  }

  def eventLogRowByLookupRowInfo(value: String, name: String, category: String): Future[Option[EventLogRow]] = {

    lookups.byValueAndNameAndCategory(value, name, category)
      .map(_.headOption)
      .flatMap {
        _.map { y =>
          events.byIdAndCat(y.key, y.category).map(_.headOption)
        }.getOrElse {
          Future.successful(None)
        }
      }
  }

  def eventLogRowByLookupValueAndCategory(value: String, category: String): Future[Seq[EventLogRow]] = {

    lookups.byValueAndCategory(value, category)
      .flatMap { x =>
        Future.sequence {
          x.distinct.map { y =>
            events.byIdAndCat(y.key, y.name)
          }

        }.map(_.flatten)

      }
  }

}
