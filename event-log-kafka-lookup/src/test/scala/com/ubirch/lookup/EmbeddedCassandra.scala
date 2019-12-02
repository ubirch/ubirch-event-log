package com.ubirch.lookup

import com.github.nosan.embedded.cassandra.local.LocalCassandraFactoryBuilder
import com.github.nosan.embedded.cassandra.test.TestCassandra
import com.typesafe.scalalogging.LazyLogging

trait EmbeddedCassandra extends LazyLogging {

  val factory = new LocalCassandraFactoryBuilder().setJvmOptions("-Xms512m -Xmx1024m").build

  logger.info("CASSANDRA CONFIG: " + factory.getJvmOptions.toString)

  val cassandra = new TestCassandra(factory)

}
