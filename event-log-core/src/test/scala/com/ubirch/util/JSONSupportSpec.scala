package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.{ Entities, TestBase }
import org.scalatest.mockito.MockitoSugar

class JSONSupportSpec extends TestBase with MockitoSugar with LazyLogging {

  "EventLog" must {

    "ToJson -> FromString be the same" in {

      val entity = Entities.Events.eventExample()

      val toJson = EventLogJsonSupport.ToJson[EventLog](entity)

      val stringEntity = toJson.toString

      val fromString = EventLogJsonSupport.FromString[EventLog](stringEntity)

      val fromStringEntity = fromString.get

      assert(entity == fromStringEntity)

    }

    "ToJson -> FromJson be the same" in {

      val entity = Entities.Events.eventExample()

      val toJson = EventLogJsonSupport.ToJson[EventLog](entity)

      val entityToBe = EventLogJsonSupport.FromJson[EventLog](toJson.get)

      assert(entity == entityToBe.get)

      assert(toJson.toString == entityToBe.toString)

    }

    "ToJson -> FromString be the same in Base64" in {

      val entity = Entities.Events.eventExample()

      val toJson = EventLogJsonSupport.ToJson[EventLog](entity)

      val stringEntity = toJson.toBase64String

      val fromString = EventLogJsonSupport.FromString[EventLog](stringEntity)

      val fromStringEntity = fromString.getFromBase64

      assert(entity == fromStringEntity)

    }

    "ToJson -> FromString be the same in Base64 2" in {

      val entity = Map("Hello" -> "Hola")

      val toJson = EventLogJsonSupport.ToJson(entity)

      val stringEntity = toJson.toBase64String

      val fromString = EventLogJsonSupport.FromString[Map[String, String]](stringEntity)

      val fromStringEntity = fromString.getFromBase64

      assert(entity.map(x => (x._1.toLowerCase, x._2)) == fromStringEntity)

    }

  }

  "Errors" must {

    "ToJson -> FromString be the same" in {

      val entity = Entities.Errors.errorExample()

      val toJson = EventLogJsonSupport.ToJson[Error](entity)

      val stringEntity = toJson.toString

      val fromString = EventLogJsonSupport.FromString[Error](stringEntity)

      val fromStringEntity = fromString.get

      assert(entity == fromStringEntity)

    }

    "ToJson -> FromJson be the same" in {

      val entity = Entities.Errors.errorExample()

      val toJson = EventLogJsonSupport.ToJson[Error](entity)

      val fromString = EventLogJsonSupport.FromJson[Error](toJson.get)

      assert(entity == fromString.get)

      assert(toJson.toString == fromString.toString)

    }

  }

}
