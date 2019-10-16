package com.ubirch

import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec, WordSpecLike }
import org.scalatra.test.scalatest.{ ScalatraSuite, ScalatraWordSpec }

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

trait TestBase
  extends ScalatraWordSpec
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with EmbeddedKafka
  with EmbeddedCassandra {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

}
