package com.ubirch.models

import com.ubirch.services.cluster.ConnectionService
import io.getquill.{ CassandraAsyncContext, EntityQuery, Insert, Quoted, SnakeCase }

import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

trait LookupKeyQueries extends TablePointer[LookupKeyRow] with CustomEncodings[LookupKeyRow] {

  import db._

  //These represent query descriptions only

  implicit val eventSchemaMeta: db.SchemaMeta[LookupKeyRow] = schemaMeta[LookupKeyRow]("lookups")

  def selectAllQ: Quoted[EntityQuery[LookupKeyRow]] = quote(query[LookupKeyRow])

  def byValueAndNameAndCategoryQ(value: String, name: String, category: String) = quote {
    query[LookupKeyRow]
      .filter(_.value == lift(value))
      .filter(_.category == lift(category))
      .filter(_.name == lift(name))
  }

  def byValueAndCategoryQ(value: String, category: String) = quote {
    query[LookupKeyRow]
      .filter(_.value == lift(value))
      .filter(_.category == lift(category))
  }

  def insertQ(lookupKeyRow: LookupKeyRow): Quoted[Insert[LookupKeyRow]] = quote {
    query[LookupKeyRow].insert(lift(lookupKeyRow))
  }

}

@Singleton
class Lookups @Inject() (val connectionService: ConnectionService)(implicit val ec: ExecutionContext) extends LookupKeyQueries {

  val db: CassandraAsyncContext[SnakeCase] = connectionService.context

  import db._

  //These actually run the queries.

  def selectAll: Future[List[LookupKeyRow]] = run(selectAllQ)

  def byValueAndNameAndCategory(value: String, name: String, category: String) = {
    run(byValueAndNameAndCategoryQ(value, name, category))
  }

  def byValueAndCategory(value: String, category: String) = {
    run(byValueAndCategoryQ(value, category))
  }

  def insert(lookupKey: LookupKeyRow): Future[RunActionResult] = run(insertQ(lookupKey))

}
