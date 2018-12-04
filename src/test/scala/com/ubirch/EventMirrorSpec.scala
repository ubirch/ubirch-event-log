package com.ubirch

import com.ubirch.models.{ CustomEncodingsBase, EventLog }
import io.getquill.{ CassandraMirrorContext, _ }
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods.parse
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

trait EventDAOMirrorBase extends CustomEncodingsBase {

  val mirrorDB = new CassandraMirrorContext(SnakeCase)
  import mirrorDB._

  implicit def encodeJValue = MappedEncoding[JValue, String](Option(_).map(_.toString).getOrElse(""))
  implicit def decodeJValue = MappedEncoding[String, JValue](x ⇒ parse(x))

  object Events {

    implicit val eventSchemaMeta = schemaMeta[EventLog]("events")

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

      assert(runQuery == "SELECT id, service_class, category, event, event_time, signature, created, updated FROM events")

    }

  }

}

