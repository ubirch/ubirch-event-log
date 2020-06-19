package com.ubirch.verification.service

import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait TestBase
  extends AsyncWordSpec
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with MustMatchers
    with EmbeddedKafka {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

}
