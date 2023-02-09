package com.ubirch.models

import io.getquill.context.cassandra.CassandraContext
import io.getquill.context.cassandra.encoding.{ Decoders, Encoders }

/**
  * Cassandra Context DB value.
  */
trait CassandraBase {

  val db: CassandraContext[_] with Encoders with Decoders

}
