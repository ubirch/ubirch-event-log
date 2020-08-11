package com.ubirch.lookup

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra
import com.typesafe.scalalogging.LazyLogging

trait EmbeddedCassandra extends LazyLogging {

  val factory = new LocalCassandraFactoryBuilder().setAllowRoot(true).build

  val cassandra = new TestCassandra(factory)

}
