package com.ubirch.services.kafka.consumer

import java.util
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }

import com.ubirch.services.execution.Execution
import com.ubirch.util.Exceptions._
import com.ubirch.util.Implicits.{ enrichedInstant, enrichedIterator }
import com.ubirch.util.{ FutureHelper, ShutdownableThread, UUIDHelper, VersionedLazyLogging }
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.Deserializer
import org.joda.time.Instant

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ Future, blocking }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

/**
  * Represents the result that is expected result for the consumption.
  * This is helpful to return the consumer record and an identifiable record.
  * This type is usually extended to support customized data.
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
trait ProcessResult[K, V] {

  val id: UUID = UUIDHelper.randomUUID

  val consumerRecord: ConsumerRecord[K, V]

}

/**
  * Represents the the type that is actually processes the consumer records.
  * The consumer doesn't care about how it is processed, it can be with
  * Futures, Actors, as long as the result type matches.
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
trait ConsumerRecordsController[K, V] {

  def process[A >: ProcessResult[K, V]](consumerRecord: ConsumerRecord[K, V]): Future[A]

}

/**
  * Represents a Consumer Runner for a Kafka Consumer.
  * It supports back-pressure using the pause/unpause. The pause duration is amortized.
  * It supports plugging rebalance strategies.
  * It supports autocommit and not autocommit.
  * It supports commit attempts.
  * It supports to "floors" for exception management. This is allows to escalate exceptions.
  * @param name Represents the Thread name
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
abstract class ConsumerRunner[K, V](name: String)
  extends ShutdownableThread(name) with Execution with ConsumerRebalanceListener with VersionedLazyLogging {

  override val version: AtomicInteger = ConsumerRunner.version

  private var consumer: Consumer[K, V] = _

  @BeanProperty var props: Map[String, AnyRef] = Map.empty

  @BeanProperty var topics: Set[String] = Set.empty

  @BeanProperty var pollTimeout: java.time.Duration = java.time.Duration.ofMillis(1000)

  @BeanProperty var pauseDuration: FiniteDuration = 1000 millis

  @BeanProperty var keyDeserializer: Option[Deserializer[K]] = None

  @BeanProperty var valueDeserializer: Option[Deserializer[V]] = None

  @BeanProperty var consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener] = None

  @BeanProperty var useSelfAsRebalanceListener: Boolean = true

  @BeanProperty var consumerRecordsController: Option[ConsumerRecordsController[K, V]] = None

  @BeanProperty var useAutoCommit: Boolean = false
  //This one is made public for testing purposes
  @BeanProperty val isPaused: AtomicBoolean = new AtomicBoolean(false)

  @BeanProperty var maxCommitAttempts = 3

  @BeanProperty var delaySingleRecord: FiniteDuration = 0 millis

  @BeanProperty var delayRecords: FiniteDuration = 0 millis

  def process(consumerRecord: ConsumerRecord[K, V]): Future[ProcessResult[K, V]]

  private val pauses = new AtomicInteger(0)

  private val isPauseUpwards = new AtomicBoolean(true)

  private def amortizePauseDuration(): FiniteDuration = {
    // 10 => 512 => about 8.5 min
    if (pauses.get() == 10) {
      isPauseUpwards.set(false)
    } else if (pauses.get() == 0) {
      isPauseUpwards.set(true)
    }

    val ps = if (isPauseUpwards.get()) {
      pauses.getAndIncrement()
    } else {
      pauses.getAndDecrement()
    }

    val pauseDuration = getPauseDuration.toMillis

    val amortized = scala.math.pow(2, ps).toInt * pauseDuration

    FiniteDuration(amortized, MILLISECONDS)

  }

  override def execute(): Unit = {
    try {
      createConsumer(getProps)
      subscribe(getTopics.toList, getConsumerRebalanceListenerBuilder)

      val failed = new AtomicReference[Option[Throwable]](None)
      val commitAttempts = new AtomicInteger(getMaxCommitAttempts)

      def commit(): Unit = consumer.commitSync()

      while (getRunning) {

        try {

          val startInstant = new Instant()

          val consumerRecords = consumer.poll(pollTimeout)
          val count = consumerRecords.count()

          if (!getIsPaused.get() && count > 0) {

            val batchCountDown = new CountDownLatch(count)
            def processRecord(consumerRecord: ConsumerRecord[K, V]) = {
              val processing = process(consumerRecord)

              processing.onComplete {
                case Success(_) =>
                  batchCountDown.countDown()
                case Failure(e) =>
                  failed.set(Some(e))
                  batchCountDown.countDown()
              }

              processing
            }

            lazy val iterator = consumerRecords.iterator().asScala

            if (getDelaySingleRecord == 0.millis && getDelayRecords == 0.millis) {
              iterator.foreach(processRecord)
            } else {
              iterator.delayOnNext(getDelaySingleRecord).consumeWithFinalDelay(processRecord)(getDelayRecords)
            }

            //TODO: probably we should add a timeout
            batchCountDown.await()

          }

          failed.getAndSet(None).foreach { f => throw f }

          if (!getUseAutoCommit && count > 0) {
            val finishTime = new Instant()
            val seconds = startInstant.millisBetween(finishTime)
            commit()
            logger.debug("Polled and Committed ... [{} records] ... [{} millis]", count, seconds)
          }

        } catch {
          case _: NeedForPauseException =>
            import monix.execution.Scheduler.{ global => scheduler }
            val partitions = consumer.assignment()
            consumer.pause(partitions)
            getIsPaused.set(true)
            val pause = amortizePauseDuration()
            scheduler.scheduleOnce(pause) {
              failed.set(Some(NeedForResumeException(s"Restarting after a $pause millis sleep...")))
            }
            logger.debug(s"NeedForPauseException: duration[{}] pause cycle[{}] partitions[{}]", pause, pauses.get(), partitions.toString)
          case e: NeedForResumeException =>
            logger.debug("NeedForResumeException: [{}]", e.getMessage)
            val partitions = consumer.assignment()
            consumer.resume(partitions)
            getIsPaused.set(false)
          case e: TimeoutException =>
            logger.error("Commit timed out {}", e.getMessage)
            logger.error("Trying one more time")
            val currentAttempts = commitAttempts.decrementAndGet()
            if (currentAttempts == 0) {
              throw MaxNumberOfCommitAttemptsException("Error Committing", s"$commitAttempts attempts were performed. But none worked. Escalating ...", Left(e))
            } else {
              commit()
            }
          case e: CommitFailedException =>
            logger.error("Commit failed {}", e.getMessage)
            val currentAttempts = commitAttempts.decrementAndGet()
            if (currentAttempts == 0) {
              throw MaxNumberOfCommitAttemptsException("Error Committing", s"$commitAttempts attempts were performed. But none worked. Escalating ...", Right(e))
            }
          case e: Throwable =>
            logger.error("Exception floor (1) ... Exception: [{}] Message: [{}]", e.getClass.getCanonicalName, e.getMessage)
            throw e

        }

      }

    } catch {
      case e: MaxNumberOfCommitAttemptsException =>
        logger.error("MaxNumberOfCommitAttemptsException: {}", e.getMessage)
        startGracefulShutdown()
      case e: ConsumerCreationException =>
        logger.error("ConsumerCreationException: {}", e.getMessage)
        startGracefulShutdown()
      case e: EmptyTopicException =>
        logger.error("EmptyTopicException: {}", e.getMessage)
        startGracefulShutdown()
      case e: NeedForShutDownException =>
        logger.error("NeedForShutDownException: {}", e.getMessage)
        startGracefulShutdown()
      case e: Exception =>
        logger.warn("Exception floor (0) ... Exception: [{}] Message: [{}]", e.getClass.getCanonicalName, e.getMessage)
        startGracefulShutdown()
    } finally {
      if (consumer != null) consumer.close()
    }
  }

  @throws(classOf[ConsumerCreationException])
  def createConsumer(props: Map[String, AnyRef]): Unit = {
    if (keyDeserializer.isEmpty && valueDeserializer.isEmpty) {
      throw ConsumerCreationException("No Serializers Found", "Please set the serializers for the key and value.")
    }

    if (props.isEmpty) {
      throw ConsumerCreationException("No Properties Found", "Please, set the properties for the consumer creation.")
    }

    try {
      val kd = keyDeserializer.get
      val vd = valueDeserializer.get

      kd.configure(props.asJava, true)
      vd.configure(props.asJava, false)

      val isAutoCommit: Boolean = props
        .filterKeys(x => ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG == x)
        .exists {
          case (_, b) =>
            b.toString.toBoolean
        }

      if (isAutoCommit) {
        setUseAutoCommit(true)
      } else {
        setUseAutoCommit(false)
      }

      consumer = new KafkaConsumer[K, V](props.asJava, kd, vd)

    } catch {
      case e: Exception =>
        throw ConsumerCreationException("Error Creating Consumer", e.getMessage)
    }
  }

  @throws(classOf[EmptyTopicException])
  def subscribe(topics: List[String], consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener]): Unit = {
    if (topics.nonEmpty) {
      val topicsAsJava = topics.asJavaCollection
      consumerRebalanceListenerBuilder match {
        case Some(crl) if !getUseSelfAsRebalanceListener =>
          val rebalancer = crl(consumer)
          logger.debug("Subscribing to [{}] with external rebalance strategy [{}]", topics.mkString(" "), rebalancer.getClass.getCanonicalName)
          consumer.subscribe(topicsAsJava, rebalancer)
        case _ if getUseSelfAsRebalanceListener =>
          logger.debug("Subscribing to [{}] with self rebalance strategy", topics.mkString(" "))
          consumer.subscribe(topicsAsJava, this)
        case _ =>
          logger.debug("Subscribing to [{}] with no rebalance strategy", topics.mkString(" "))
          consumer.subscribe(topicsAsJava)
      }

    } else {
      throw EmptyTopicException("Topic cannot be empty.")
    }
  }

  override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    partitionsRevoked.set(Set.empty)
    iterator.foreach { x =>
      partitionsRevoked.set(partitionsRevoked.get ++ Set(x))
      logger.debug(s"onPartitionsRevoked: [${x.topic()}-${x.partition()}]")
    }

  }

  override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    partitionsAssigned.set(Set.empty)
    iterator.foreach { x =>
      partitionsAssigned.set(partitionsAssigned.get ++ Set(x))
      logger.debug(s"OnPartitionsAssigned: [${x.topic()}-${x.partition()}]")
    }
  }

  //This is here just for testing rebalancing and it is only visible when
  //The rebalancing strategy is self mananaged
  val partitionsRevoked = new AtomicReference[Set[TopicPartition]](Set.empty)
  val partitionsAssigned = new AtomicReference[Set[TopicPartition]](Set.empty)

  def startPolling(): Unit = start()

}

/**
  * Simple Companion Object for the Consumer Runner
  */
object ConsumerRunner {
  val version: AtomicInteger = new AtomicInteger(0)
}
