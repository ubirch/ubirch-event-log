package com.ubirch.process

import com.ubirch.TestBase
import com.ubirch.sdk.process.CreateEventFrom
import com.ubirch.services.execution.Execution
import org.json4s.JsonAST.JNull
import org.scalatest.mockito.MockitoSugar

case class Hello(name: String)

class DefaultClientExecutorsSpec extends TestBase with MockitoSugar with Execution {

  "CreateEventFrom" must {
    "create Event" in {

      val data = Hello("Hola")

      val createEventFrom = new CreateEventFrom[Hello]("my_service_class", "my_category")

      val createdEvent = createEventFrom(data)

      createdEvent must not be null

    }

    "throw CreateEventFromException" in {

      val data: Hello = null

      val createEventFrom = new CreateEventFrom[Hello]("my_service_class", "my_category")

      val createdEvent = createEventFrom(data)

      createdEvent.event mustBe JNull
    }
  }

}
