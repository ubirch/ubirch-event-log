package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, Embedded, SnakeCase }
import javax.inject._
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }

trait EventBase {
  val id: UUID
  val serviceClass: String
  val category: String
  val eventTime: Date
}

trait EventMsgBase {
  val event: EventBase
  val signature: String
}

case class Event(id: UUID, serviceClass: String, category: String, event: JValue, eventTime: Date, eventTimeInfo: TimeInfo) extends Embedded with EventBase

//TODO check whether we could use org.joda.time.DateTime instead of Date
case class EventLog(event: Event, signature: String, created: Date, updated: Date) extends EventMsgBase

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

  def insert(eventlog: EventLog): db.Quoted[db.Insert[EventLog]] = insertQ(eventlog)

}