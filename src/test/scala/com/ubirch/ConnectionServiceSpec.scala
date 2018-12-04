package com.ubirch

import com.google.inject.Guice
import com.ubirch.models.Events
import com.ubirch.services.ServiceBinder
import com.ubirch.services.cluster.ConnectionService
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

class ConnectionServiceSpec extends WordSpec
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MustMatchers {

  override def afterAll(): Unit = {
    val connectionService = serviceInjector.getInstance(classOf[ConnectionService])

    val db = connectionService.context

    db.close()
  }

  val serviceInjector = Guice.createInjector(new ServiceBinder())

  val events = serviceInjector.getInstance(classOf[Events])

  "Events" must {

    "select * from events;" in {

      val runQuery = await(events.selectAll)

      runQuery.map(_.event.id) must contain theSameElementsInOrderAs Nil

    }

  }

}
