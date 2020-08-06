package com.ubirch.verification

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra
import com.typesafe.scalalogging.LazyLogging

trait EmbeddedCassandra extends LazyLogging {

  val factory = new LocalCassandraFactoryBuilder().build()

  val cassandra = new TestCassandra(factory)

}
