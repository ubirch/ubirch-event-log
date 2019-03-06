package com.ubirch.services

import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.ubirch.services.kafka.consumer.{ Configs, ConsumerRunner, ProcessResult }
import com.ubirch.util.Exceptions.NeedForPauseException
import com.ubirch.{ NameGiver, PortGiver, TestBase }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps }

class ConsumerRunnerSpec extends TestBase {

  "Consumer Runner" must {

    "fail if topic is not provided" in {

      val configs = Configs(
        bootstrapServers = "localhost:" + "9000",
        groupId = "My_Group_ID",
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-1") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          Future.successful(processResult(consumerRecord))
        }
      }

      consumer.setKeyDeserializer(Some(new StringDeserializer()))
      consumer.setValueDeserializer(Some(new StringDeserializer()))

      consumer.setProps(configs)
      consumer.startPolling()

      Thread.sleep(5000) // We wait here so the change is propagated

      assert(!consumer.getRunning)

    }

    "fail if no serializers have been set" in {

      val configs = Configs(
        bootstrapServers = "localhost:" + "9000",
        groupId = "My_Group_ID",
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-2") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          Future.successful(processResult(consumerRecord))
        }
      }

      consumer.setProps(configs)
      consumer.startPolling()

      Thread.sleep(5000) // We wait here so the change is propagated

      assert(!consumer.getRunning)

    }

    "fail if props are empty" in {

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-3") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          Future.successful(processResult(consumerRecord))
        }
      }

      consumer.setKeyDeserializer(Some(new StringDeserializer()))
      consumer.setValueDeserializer(Some(new StringDeserializer()))
      consumer.setProps(Map.empty)
      consumer.startPolling()

      Thread.sleep(5000) // We wait here so the change is propagated

      assert(!consumer.getRunning)

    }

  }

  "consumer 100 entities successfully" in {

    val maxEntities = 100
    val futureMessages = scala.collection.mutable.ListBuffer.empty[String]
    val counter = new CountDownLatch(maxEntities)

    implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    withRunningKafka {

      val topic = NameGiver.giveMeATopicName

      val messages = (1 to maxEntities).map(i => "Hello " + i).toList
      messages.foreach { m =>
        publishStringMessageToKafka(topic, m)
      }

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-4") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          futureMessages += consumerRecord.value()
          counter.countDown()
          Future.successful(processResult(consumerRecord))
        }
      }

      consumer.setKeyDeserializer(Some(new StringDeserializer()))
      consumer.setValueDeserializer(Some(new StringDeserializer()))
      consumer.setTopics(Set(topic))
      consumer.setProps(configs)
      consumer.startPolling()

      counter.await()

      assert(futureMessages.size == maxEntities)
      assert(messages == futureMessages.toList)

    }

  }

  "run an NeedForPauseException and pause and then unpause" in {
    val maxEntities = 1
    val futureMessages = scala.collection.mutable.ListBuffer.empty[String]
    val counter = new CountDownLatch(maxEntities)

    implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    withRunningKafka {

      val topic = NameGiver.giveMeATopicName

      val messages = (1 to maxEntities).map(i => "Hello " + i).toList
      messages.foreach { m =>
        publishStringMessageToKafka(topic, m)
      }

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-5") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          futureMessages += consumerRecord.value()
          counter.countDown()
          Future.failed(NeedForPauseException("Need to pause", "yeah"))
        }
      }

      consumer.setKeyDeserializer(Some(new StringDeserializer()))
      consumer.setValueDeserializer(Some(new StringDeserializer()))
      consumer.setTopics(Set(topic))
      consumer.setProps(configs)
      consumer.startPolling()

      counter.await()

      assert(futureMessages.size == maxEntities)
      assert(messages == futureMessages.toList)

      Thread.sleep(2000)

      assert(consumer.getPausedHistory.get() >= 1)

      Thread.sleep(2000)

      assert(consumer.getUnPausedHistory.get() >= 1)

    }
  }

  "run an NeedForPauseException and pause and then unpause when throttling" in {
    val maxEntities = 1
    val futureMessages = scala.collection.mutable.ListBuffer.empty[String]
    val counter = new CountDownLatch(maxEntities)

    implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

    withRunningKafka {

      val topic = NameGiver.giveMeATopicName

      val messages = (1 to maxEntities).map(i => "Hello " + i).toList
      messages.foreach { m =>
        publishStringMessageToKafka(topic, m)
      }

      val configs = Configs(
        bootstrapServers = "localhost:" + config.kafkaPort,
        groupId = "My_Group_ID",
        autoOffsetReset = OffsetResetStrategy.EARLIEST
      )

      def processResult(_consumerRecord: ConsumerRecord[String, String]) = new ProcessResult[String, String] {
        override val id: UUID = UUID.randomUUID()
        override val consumerRecord: ConsumerRecord[String, String] = _consumerRecord
      }

      val consumer = new ConsumerRunner[String, String]("cr-6") {
        override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

        override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
          futureMessages += consumerRecord.value()
          counter.countDown()
          Future.failed(NeedForPauseException("Need to pause", "yeah"))
        }
      }

      consumer.setKeyDeserializer(Some(new StringDeserializer()))
      consumer.setValueDeserializer(Some(new StringDeserializer()))
      consumer.setTopics(Set(topic))
      consumer.setProps(configs)
      consumer.setDelaySingleRecord(10 millis)
      consumer.setDelayRecords(1000 millis)

      consumer.startPolling()

      counter.await()

      assert(futureMessages.size == maxEntities)
      assert(messages == futureMessages.toList)

      Thread.sleep(2000)

      assert(consumer.getPausedHistory.get() >= 1)

      Thread.sleep(2000)

      assert(consumer.getUnPausedHistory.get() >= 1)

    }

  }

}
