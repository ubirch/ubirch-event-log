package com.ubirch.models

import io.getquill.context.cassandra.CassandraContext
import io.getquill.context.cassandra.encoding.{ Decoders, Encoders }

/**
  * Cassandra Context DB value.
  */
trait CassandraBase {

  val db: CassandraContext[_] with Encoders with Decoders

}

/**
  * Value that represents a pointer to a Cassandra Table.
  * Very useful for mapping different versions for a particular table.
  * @tparam T represents the scala value that represent the database table.
  */

trait TablePointer[T] extends CassandraBase {

  import db._

  implicit val eventSchemaMeta: SchemaMeta[T]

}
