package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents the queries linked to the EventLogRow case class and to the Events Table
  */
trait EventLogByCatQueries extends TablePointer[EventLogRow] with CustomEncodings[EventLogRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[EventLogRow] = schemaMeta[EventLogRow]("events_by_cat")

  def byCatAndYearAndMonthAndDayQ(category: String, year: Int, month: Int, day: Int) = quote {
    query[EventLogRow]
      .filter(x => x.category == lift(category))
      .filter(x => x.eventTimeInfo.year == lift(year))
      .filter(x => x.eventTimeInfo.month == lift(month))
      .filter(x => x.eventTimeInfo.day == lift(day))
      .map(x => x)
  }

}

/**
  * Represent the materialization of the queries. Queries here are actually executed and
  * a concrete connection context is injected.
  * @param connectionService Represents the db connection value that is injected.
  * @param ec Represent the execution context for asynchronous processing.
  */
@Singleton
class EventsByCat @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends EventLogByCatQueries {

  val db: CassandraAsyncContext[SnakeCase.type] = connectionService.context

  import db._

  //These actually run the queries.

  def byCatAndYearAndMonthAndDay(category: String, year: Int, month: Int, day: Int): Future[List[EventLogRow]] =
    run(byCatAndYearAndMonthAndDayQ(category, year, month, day))

}
