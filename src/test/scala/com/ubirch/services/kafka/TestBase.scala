package com.ubirch.services.kafka

import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

trait TestBase
    extends WordSpec
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MustMatchers
    with EmbeddedKafka {

  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

}