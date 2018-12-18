package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.kafka.TestBase
import org.scalatest.mockito.MockitoSugar

import scala.language.{ implicitConversions, postfixOps }

class ExecutorSpec extends TestBase with MockitoSugar with LazyLogging {

  "ExecutorSpec" must {

    "pass the same result as it comes in" in {

      val executor = new Executor[String, String] {
        override def apply(v1: String): String = v1
      }

      assert(executor("Hello World") == "Hello World")

    }

    "compose two executors" in {

      val executor1 = new Executor[String, String] {
        override def apply(v1: String): String = v1.toLowerCase()
      }

      val executor2 = new Executor[String, String] {
        override def apply(v1: String): String = v1.reverse
      }

      val composed = executor1 andThen executor2

      assert(composed("Hello World") == "dlrow olleh")

    }

  }

}