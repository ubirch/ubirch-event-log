package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.{ Entities, TestBase }
import com.ubirch.models.{ Error, EventLog }
import org.scalatest.mockito.MockitoSugar

class JSONSupportSpec extends TestBase with MockitoSugar with LazyLogging {

  "EventLog" must {

    "ToJson -> FromString be the same" in {

      val entity = Entities.Events.eventExample()

      val toJson = ToJson[EventLog](entity)

      val stringEntity = toJson.toString

      val fromString = FromString[EventLog](stringEntity)

      val fromStringEntity = fromString.get

      assert(entity == fromStringEntity)

    }

    "ToJson -> FromJson be the same" in {

      val entity = Entities.Events.eventExample()

      val toJson = ToJson[EventLog](entity)

      val fromString = FromJson[EventLog](toJson.get)

      assert(entity == fromString.get)

      assert(toJson.toString == fromString.toString)

    }

  }

  "Errors" must {

    "ToJson -> FromString be the same" in {

      val entity = Entities.Errors.errorExample()

      val toJson = ToJson[Error](entity)

      val stringEntity = toJson.toString

      val fromString = FromString[Error](stringEntity)

      val fromStringEntity = fromString.get

      assert(entity == fromStringEntity)

    }

    "ToJson -> FromJson be the same" in {

      val entity = Entities.Errors.errorExample()

      val toJson = ToJson[Error](entity)

      val fromString = FromJson[Error](toJson.get)

      assert(entity == fromString.get)

      assert(toJson.toString == fromString.toString)

    }

  }

}
