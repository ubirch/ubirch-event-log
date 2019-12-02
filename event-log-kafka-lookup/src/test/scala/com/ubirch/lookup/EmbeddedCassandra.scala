package com.ubirch.lookup

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra
import com.typesafe.scalalogging.LazyLogging

trait EmbeddedCassandra extends LazyLogging {

  val factory = new LocalCassandraFactoryBuilder().addJvmOptions("-Xms512m").addJvmOptions("-Xmx1024m").build()

  val cassandra = new TestCassandra(factory)

}
