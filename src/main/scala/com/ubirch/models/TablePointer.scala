package com.ubirch.models

import io.getquill.context.cassandra.CassandraContext
import io.getquill.context.cassandra.encoding.{ Decoders, Encoders }

trait CassandraBase {

  val db: CassandraContext[_] with Encoders with Decoders

}

trait TablePointer[T] extends CassandraBase {

  import db._

  implicit val eventSchemaMeta: SchemaMeta[T]

}
