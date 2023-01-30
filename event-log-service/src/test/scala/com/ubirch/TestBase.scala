package com.ubirch

import com.ubirch.util.cassandra.test.EmbeddedCassandraBase
import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

trait TestBase
  extends ScalatraWordSpec
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with EmbeddedKafka
  with EmbeddedCassandraBase {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

}
