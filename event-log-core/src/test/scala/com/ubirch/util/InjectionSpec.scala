package com.ubirch.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.TestBase
import com.ubirch.util.Exceptions.{ InjectionException, InjectorCreationException }
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext
import scala.util.Failure

trait NotReal

class InjectionSpec extends TestBase with MockitoSugar with LazyLogging {

  "Injection Spec" must {

    "throw InjectorCreatorException" in {

      object X extends InjectorHelper(null)

      assertThrows[InjectorCreationException](X.get[NotReal])

    }

    "throw InjectionException" in {

      def inject = InjectorHelper.get[NotReal]

      assertThrows[InjectionException](inject)

    }

    "get correct Type" in {

      def inject = InjectorHelper.get[ExecutionContext]

      assert(inject.isInstanceOf[ExecutionContext])

    }

    "get correct Type As Option" in {

      def inject = InjectorHelper.getAsOption[ExecutionContext]

      assert(inject.isDefined)
      assert(inject.exists(_.isInstanceOf[ExecutionContext]))

    }

    "get correct Type As Try" in {

      def inject = InjectorHelper.getAsTry[ExecutionContext]

      assert(inject.isSuccess)
      assert(inject.map(_.isInstanceOf[ExecutionContext]).getOrElse(false))

    }

    "get correct Type As Try 2" in {

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
