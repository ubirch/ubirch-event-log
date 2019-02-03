package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.EventLogParser
import com.ubirch.services.ServiceBinder
import com.ubirch.util.Exceptions.{ InjectionException, InjectorCreationException }
import com.ubirch.{ Entities, TestBase }
import org.scalatest.mockito.MockitoSugar

import scala.util.{ Failure, Success }

class InjectionSpec extends TestBase with MockitoSugar with LazyLogging {

  "Injection Spec" must {

    "throw InjectorCreatorException" in {

      trait NotReal

      object X extends InjectorHelper {
        override val modules: List[ServiceBinder] = null
      }

      assertThrows[InjectorCreationException](X.get[NotReal])

    }

    "throw InjectionException" in {

      trait NotReal

      def inject = InjectorHelper.get[NotReal]

      assertThrows[InjectionException](inject)

    }

    "get correct Type" in {

      def inject = InjectorHelper.get[EventLogParser]

      assert(inject.isInstanceOf[EventLogParser])

    }

    "get correct Type As Option" in {

      def inject = InjectorHelper.getAsOption[EventLogParser]

      assert(inject.isDefined)
      assert(inject.exists(_.isInstanceOf[EventLogParser]))

    }

    "get correct Type As Try" in {

      def inject = InjectorHelper.getAsTry[EventLogParser]

      assert(inject.isSuccess)
      assert(inject.map(_.isInstanceOf[EventLogParser]).getOrElse(false))

    }

    "get correct Type As Try 2" in {

      trait NotReal

      def inject = InjectorHelper.getAsTry[NotReal]

      assert(inject.isFailure)

      inject match {
        case Failure(_: InjectionException) =>
          assert(1 == 1)
        case _ =>
          fail("Expecting Exception")

      }

    }

  }

}
