package com.ubirch

import com.ubirch.models.{ Event, EventLog }
import io.getquill.{ CassandraMirrorContext, _ }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

trait EventDAOMirrorBase {

  val mirrorDB = new CassandraMirrorContext(Literal)
  import mirrorDB._

  object Events {

    implicit val eventSchemaMeta = schemaMeta[EventLog]("events", _.event.serviceClass -> "service_class")

    def selectAllQ = quote(query[EventLog])

    def insertQ(event: EventLog) = quote {
      query[EventLog].insert(lift(event))
    }

  }

}

class QuillMirrorSpec extends WordSpec
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MustMatchers
    with EventDAOMirrorBase {

  import mirrorDB._

  "Events Statements" must {

    "mirror select * from events;" in {

      val runQuery = mirrorDB.run(Events.selectAllQ).string

      assert(runQuery == "SELECT id, service_class, category, signature, created, updated FROM events")

    }

  }

}

