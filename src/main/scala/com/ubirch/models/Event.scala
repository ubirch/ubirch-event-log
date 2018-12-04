package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.services.cluster.ConnectionService
import io.getquill.Embedded
import javax.inject._
import org.json4s.JValue

import scala.concurrent.ExecutionContext

case class TimeInfo(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) extends Embedded

case class Event(id: UUID, serviceClass: String, category: String, event: JValue, eventTime: Date, timeInfo: TimeInfo) extends Embedded

case class EventLog(event: Event, signature: String, created: Date, updated: Date)

trait EventLogQueries extends TablePointer[EventLog] with CustomEncodings[EventLog] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta = schemaMeta[EventLog]("events")

  def selectAllQ = quote(query[EventLog])

  def insertQ(eventlog: EventLog) = quote {
    query[EventLog].insert(lift(eventlog))
  }

}

@Singleton
class Events @Inject() (val connectionService: ConnectionService)(implicit ec: ExecutionContext) extends EventLogQueries {

  val db = connectionService.context

  import db._

  //These actually run the queries.

  def selectAll = run(selectAllQ)

  def insert(eventlog: EventLog) = insertQ(eventlog)

}