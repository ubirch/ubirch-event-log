package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, SnakeCase }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

trait LookupKeyQueries extends TablePointer[LookupKeyRow] with CustomEncodings[LookupKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[LookupKeyRow] = schemaMeta[LookupKeyRow]("lookups")

  def selectAllQ: db.Quoted[db.EntityQuery[LookupKeyRow]] = quote(query[LookupKeyRow])

  def insertQ(lookupKeyRow: LookupKeyRow): db.Quoted[db.Insert[LookupKeyRow]] = quote {
    query[LookupKeyRow].insert(lift(lookupKeyRow))
  }

}

@Singleton
class Lookups @Inject() (val connectionService: ConnectionService)(implicit ec: ExecutionContext) extends LookupKeyQueries {

  val db: CassandraAsyncContext[SnakeCase.type] = connectionService.context

  import db._

  //These actually run the queries.

  def selectAll: Future[List[LookupKeyRow]] = run(selectAllQ)

  def insert(lookupKey: LookupKeyRow): Future[RunActionResult] = run(insertQ(lookupKey))

}
