package com.ubirch.services.cluster

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.Guice
import com.ubirch.services.ServiceBinder
import com.ubirch.services.execution.ExecutionImpl
import com.ubirch.{ EmbeddedCassandra, TestBase }

class ClusterSpec extends TestBase with EmbeddedCassandra with ExecutionImpl {

  val serviceInjector = Guice.createInjector(new ServiceBinder())

  "Cluster and Cassandra Context" must {

    "be able to get proper instance and do query" in {

      val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

      val db = connectionService.context

      val t = db.executeQuery("SELECT * FROM Events")

      assert(await(t).nonEmpty)
    }

    "be able to get proper instance and do query without recreating it" in {

      val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

      val db = connectionService.context

      val t = db.executeQuery("SELECT * FROM Events")
      assert(await(t).nonEmpty)
    }

  }

  override protected def afterAll(): Unit = {

    val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

    val db = connectionService.context

    db.close()

    cassandra.stop()
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    cassandra.executeScripts(
      CqlScript.statements("CREATE KEYSPACE IF NOT EXISTS event_log  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"),
      CqlScript.statements("drop table if exists event_log.events;"),
      CqlScript.statements("USE event_log;"),
      CqlScript.statements(
        """|create table if not exists events (
           |    id timeuuid ,
           |    principal text ,
           |    category text ,
           |    event_source_service text ,
           |    device_id UUID,
           |    year int ,
           |    month int ,
           |    day int ,
           |    hour int ,
           |    minute int ,
           |    second int,
           |    milli int,
           |    PRIMARY KEY ((principal, category), year, month, day, hour, device_id)
           |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin
      ),
      CqlScript.statements(
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'Regio IT', 'Validate', 'Avatar-Service', 522f3e64-6ee5-470c-8b66-9edb0cfbf3b1, 2018, 11, 1, 7, 15, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'Regio IT', 'Anchor', 'Avatar-Service', 522f3e64-6ee5-470c-8b66-9edb0cfbf3b1, 2018, 11, 1, 7, 17, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'Regio IT', 'Validate', 'Avatar-Service', 522f3e64-6ee5-470c-8b66-9edb0cfbf3b1, 2018, 11, 2, 8, 15, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'Regio IT', 'Anchor', 'Avatar-Service', 522f3e64-6ee5-470c-8b66-9edb0cfbf3b1, 2018, 11, 2, 8, 17, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'MunichRE', 'Validate', 'Avatar-Service', 41245902-69a0-450c-8d37-78e34f0e6760, 2018, 10, 1, 9, 15, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'MunichRE', 'Anchor', 'Avatar-Service', 41245902-69a0-450c-8d37-78e34f0e6760, 2018, 11, 1, 9, 17, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'MunichRE', 'Validate', 'Avatar-Service', 41245902-69a0-450c-8d37-78e34f0e6760, 2018, 11, 2, 11, 15, 0, 0);",
        "insert into events (id, principal, category, event_source_service, device_id, year, month, day, hour, minute, second, milli) values (now(), 'RMunichRET', 'Anchor', 'Avatar-Service', 41245902-69a0-450c-8d37-78e34f0e6760, 2018, 11, 2, 11, 17, 0, 0);"
          .stripMargin
      )
    )
  }
}
