package com.ubirch.services.kafka

import net.manub.embeddedkafka.EmbeddedKafka
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

trait TestBase
  extends WordSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with MustMatchers
  with EmbeddedKafka