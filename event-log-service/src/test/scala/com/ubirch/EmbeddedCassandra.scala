package com.ubirch

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra

trait EmbeddedCassandra {

  val factory = new LocalCassandraFactoryBuilder().build

  val cassandra = new TestCassandra(factory)

}
