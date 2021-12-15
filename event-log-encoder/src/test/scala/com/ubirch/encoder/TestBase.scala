package com.ubirch.encoder

import java.util.concurrent.TimeoutException

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig, KafkaUnavailableException }
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpec }

import scala.annotation.tailrec
import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }

trait TestBase
  extends WordSpec
  with ScalaFutures
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with MustMatchers
  with EmbeddedKafka
  with LazyLogging {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

  def readMessage(topic: String, onStartWait: Long = 5000, maxRetries: Int = 10, maxToRead: Int = 1, sleepInBetween: Long = 500)(implicit kafkaConfig: EmbeddedKafkaConfig): List[String] = {
    @tailrec
    def go(acc: Int): List[String] = {
      try {
        logger.info("Trying to get value(s) from [{}]", topic)
        val read = {
          consumeNumberMessagesFromTopics(Set(topic), maxToRead, autoCommit = false, timeout = 20.seconds)(
            kafkaConfig,
            new StringDeserializer()
          )(topic)
        }
        logger.info("[{}] messages read", read.size)
        read
      } catch {
        case e: KafkaUnavailableException =>
          throw e
        case e: TimeoutException =>
          logger.warn("Starting retry")
          if (acc == 0) {
            throw e
          } else {
            Thread.sleep(sleepInBetween)
            go(acc - 1)
          }
      }
    }

    if (onStartWait > 0) {
      Thread.sleep(onStartWait)
    }

    go(maxRetries)
  }

}
