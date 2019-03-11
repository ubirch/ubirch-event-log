package com.ubirch.kafka.consumer

import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.ubirch.TestBase
import com.ubirch.kafka.util.Exceptions.{ CommitTimeoutException, NeedForPauseException }
import com.ubirch.util.{ NameGiver, PortGiver }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ ConsumerRecord, ConsumerRecords, OffsetResetStrategy }
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
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

    "consumer 100 entities successfully" in {

      val maxEntities = 100
      val futureMessages = scala.collection.mutable.ListBuffer.empty[String]
      val counter = new CountDownLatch(maxEntities)

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

    "consume complete after error" in {
      val maxEntities = 10
      val futureMessages = scala.collection.mutable.ListBuffer.empty[String]

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

        val doErrorOn: Int = {
          val start = 1
          val end = maxEntities
          val rnd = new scala.util.Random
          start + rnd.nextInt((end - start) + 1)
        }

        var alreadyFailed = false
        var current = 1

        val consumer = new ConsumerRunner[String, String]("cr-7") {
          override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

          override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
            current = current + 1
            if (current == doErrorOn && !alreadyFailed) {
              futureMessages.clear()
              alreadyFailed = true
              Future.failed(NeedForPauseException("Need to pause", "yeah"))
            } else {
              futureMessages += consumerRecord.value()
              Future.successful(processResult(consumerRecord))
            }

          }
        }

        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)
        consumer.startPolling()

        Thread.sleep(10000)

        assert(futureMessages.toSet == messages.toSet)
        assert(futureMessages.toSet.size == maxEntities)

      }
    }

    "try to commit after TimeoutException" in {

      val maxEntities = 1
      val attempts = new CountDownLatch(3)

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

        val consumer: ConsumerRunner[String, String] = new ConsumerRunner[String, String]("cr-8") {
          override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

          override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
            Future.successful(processResult(consumerRecord))
          }

          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                attempts.countDown()
                throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
              }
            }

          }
        }

        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)
        consumer.startPolling()

        attempts.await()
        assert(attempts.getCount == 0)

      }

    }

    "try to commit after TimeoutException and another Exception" in {
      val maxEntities = 1
      val attempts = new CountDownLatch(4)

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

        val consumer: ConsumerRunner[String, String] = new ConsumerRunner[String, String]("cr-9") {
          override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

          override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
            Future.successful(processResult(consumerRecord))
          }

          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                attempts.countDown()
                if (attempts.getCount == 2) {
                  attempts.countDown()
                  attempts.countDown()
                  throw new Exception("Another exception")
                } else {
                  throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
                }
              }
            }

          }
        }

        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)
        consumer.startPolling()

        attempts.await()
        assert(attempts.getCount == 0)

      }
    }

    "try to commit after TimeoutException and OK after" in {
      val maxEntities = 1

      val committed = new CountDownLatch(1)
      val failed = new CountDownLatch(3)
      var committedN = 0

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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

        val consumer: ConsumerRunner[String, String] = new ConsumerRunner[String, String]("cr-9") {
          override implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

          override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
            Future.successful(processResult(consumerRecord))
          }

          override def createProcessRecords(
              currentPartitionIndex: Int,
              currentPartition: TopicPartition,
              allPartitions: Set[TopicPartition],
              consumerRecords: ConsumerRecords[String, String]
          ): ProcessRecords = {

            new ProcessRecords(currentPartitionIndex, currentPartition, allPartitions, consumerRecords) {
              override def commitFunc(): Vector[Unit] = {
                failed.countDown()
                if (failed.getCount == 1) {
                  val f = super.commitFunc()
                  failed.countDown()
                  committed.countDown()
                  f
                } else {
                  throw CommitTimeoutException("Commit timed out", () => commitFunc(), new TimeoutException("Timed out"))
                }
              }
            }

          }
        }

        consumer.setKeyDeserializer(Some(new StringDeserializer()))
        consumer.setValueDeserializer(Some(new StringDeserializer()))
        consumer.setTopics(Set(topic))
        consumer.setProps(configs)
        consumer.onPostCommit(i => committedN = i)
        consumer.startPolling()

        committed.await()
        failed.await()
        assert(committedN == 1)
        assert(committed.getCount == 0)
        assert(failed.getCount == 0)

      }
    }

  }

}
