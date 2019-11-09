package com.ubirch.kafka.consumer

import java.util
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.{ Collections, UUID }

import com.ubirch.kafka.util.Exceptions._
import com.ubirch.kafka.util.Implicits._
import com.ubirch.kafka.util.{ Callback, Callback0, VersionedLazyLogging }
import com.ubirch.util.{ FutureHelper, ShutdownableThread }
import org.apache.kafka.clients.consumer.{ OffsetAndMetadata, _ }
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.serialization.Deserializer

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

trait WithProcessRecords[K, V] {

  cr: ConsumerRunner[K, V] =>

  trait ProcessRecordsBase {

    val consumerRecords: ConsumerRecords[K, V]

    protected val batchCountDownSize: Int

    protected lazy val failed = new AtomicReference[Option[Throwable]](None)

    protected lazy val batchCountDown: CountDownLatch = new CountDownLatch(batchCountDownSize)

    def run(): Vector[Unit] = {
      start()
      aggregate()
      finish()
    }

    def processRecords(consumerRecords: Vector[ConsumerRecord[K, V]]) {
      val processing = process(consumerRecords)
      processing.onComplete {
        case Success(_) =>
          batchCountDown.countDown()
        case Failure(e) =>
          failed.set(Some(e))
          batchCountDown.countDown()
      }
    }

    def start(): Unit

    def aggregate(): Unit = {
      val aggRes = batchCountDown.await(20, TimeUnit.SECONDS)
      if (!aggRes) {
        logger.warn("Taking too much time aggregating ..")
      }
    }

    def commitFunc(): Vector[Unit]

    def finish(): Vector[Unit]

  }

  class ProcessRecordsOne(
      val currentPartitionIndex: Int,
      val currentPartition: TopicPartition,
      val allPartitions: Set[TopicPartition],
      val consumerRecords: ConsumerRecords[K, V]
  ) extends ProcessRecordsBase {

    protected lazy val partitionRecords = consumerRecords.records(currentPartition).asScala.toVector

    protected lazy val partitionRecordsSize = partitionRecords.size

    override protected val batchCountDownSize: Int = partitionRecordsSize

    def start() {
      if (getDelaySingleRecord == 0.millis && getDelayRecords == 0.millis) {
        partitionRecords.foreach(x => processRecords(Vector(x)))
      } else {
        partitionRecords.toIterator
          .delayOnNext(getDelaySingleRecord)
          .consumeWithFinalDelay(x => processRecords(Vector(x)))(getDelayRecords)
      }
    }

    def commitFunc(): Vector[Unit] = {

      try {
        val lastOffset = partitionRecords(partitionRecordsSize - 1).offset()
        consumer.commitSync(Collections.singletonMap(currentPartition, new OffsetAndMetadata(lastOffset + 1)))
        postCommitCallback.run(partitionRecordsSize)
      } catch {
        case e: TimeoutException =>
          throw CommitTimeoutException("Commit timed out", () => this.commitFunc(), e)
        case e: Throwable =>
          throw e
      }

    }

    def finish(): Vector[Unit] = {
      val error = failed.get()
      if (error.isDefined) {
        val initialOffsets = allPartitions.map { p =>
          (p, consumerRecords.records(p).asScala.headOption.map(_.offset()))
        }
        initialOffsets.drop(currentPartitionIndex).foreach {
          case (p, Some(of)) => consumer.seek(p, of)
          case (_, None) =>
        }
        failed.set(None)
        throw error.get
      } else {
        commitFunc()
      }

    }

  }

  class ProcessRecordsAll(val consumerRecords: ConsumerRecords[K, V]) extends ProcessRecordsBase {

    protected val batchCountDownSize: Int = 1

    val futureHelper = new FutureHelper()

    def start() {
      processRecords(consumerRecords.iterator().asScala.toVector)
      if (getDelayRecords > 0.millis) {
        futureHelper.delay(getDelayRecords)(())
      }
    }

    def commitFuncUpgraded(): Vector[Unit] = {

      try {
        consumer.commitSync()
        postCommitCallback.run(consumerRecords.count())
      } catch {
        case e: TimeoutException =>
          throw CommitTimeoutException("Commit timed out", () => this.commitFuncUpgraded(), e)
        case e: Throwable =>
          throw e
      }

    }

    @deprecated("It makes commits slower. Do not use. Use commitFuncUpgraded. It will be removed soon.", "1.2.6")
    def commitFunc(): Vector[Unit] = {

      try {
        consumerRecords.partitions().asScala.foreach { p =>
          val partitionRecords = consumerRecords.records(p).asScala.toVector
          val partitionRecordsSize = partitionRecords.size
          val lastOffset = partitionRecords(partitionRecordsSize - 1).offset()
          consumer.commitSync(Collections.singletonMap(p, new OffsetAndMetadata(lastOffset + 1)))
        }
        postCommitCallback.run(consumerRecords.count())
      } catch {
        case e: TimeoutException =>
          throw CommitTimeoutException("Commit timed out", () => this.commitFunc(), e)
        case e: Throwable =>
          throw e
      }

    }

    def finish(): Vector[Unit] = {
      val error = failed.get()
      if (error.isDefined) {
        consumerRecords.partitions().asScala.foreach { p =>
          val offset = Option(consumer.committed(p)).map(_.offset()).getOrElse(0L)
          consumer.seek(p, offset)
        }
        failed.set(None)
        throw error.get
      } else {
        commitFuncUpgraded()
      }

    }

  }

  def allFactory(consumerRecords: ConsumerRecords[K, V]): ProcessRecordsAll = new ProcessRecordsAll(consumerRecords)

  def oneFactory(
      currentPartitionIndex: Int,
      currentPartition: TopicPartition,
      allPartitions: Set[TopicPartition],
      consumerRecords: ConsumerRecords[K, V]
  ): ProcessRecordsOne = {
    new ProcessRecordsOne(
      currentPartitionIndex,
      currentPartition,
      allPartitions,
      consumerRecords
    )
  }

}

/**
  * Represents a Consumer Runner for a Kafka Consumer.
  * It supports back-pressure using the pause/unpause. The pause duration is amortized.
  * It supports plugging rebalance listeners.
  * It supports autocommit and not autocommit.
  * It supports commit attempts.
  * It supports to "floors" for exception management. This is allows to escalate exceptions.
  *
  * @param name Represents the Thread name
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
abstract class ConsumerRunner[K, V](name: String)
  extends ShutdownableThread(name)
  with ConsumerRebalanceListener
  with WithProcessRecords[K, V]
  with VersionedLazyLogging {

  implicit def ec: ExecutionContext

  override val version: AtomicInteger = ConsumerRunner.version
  //This one is made public for testing purposes
  @BeanProperty val isPaused: AtomicBoolean = new AtomicBoolean(false)

  val partitionsRevoked = new AtomicReference[Set[TopicPartition]](Set.empty)

  val partitionsAssigned = new AtomicReference[Set[TopicPartition]](Set.empty)

  @BeanProperty val pausedHistory = new AtomicReference[Int](0)

  @BeanProperty val unPausedHistory = new AtomicReference[Int](0)
  ////Testing

  private val pauses = new AtomicInteger(0)

  private val isPauseUpwards = new AtomicBoolean(true)

  private val preConsumeCallback = new Callback0[Unit] {}

  private val postConsumeCallback = new Callback[Int, Unit] {}

  protected val postCommitCallback = new Callback[Int, Unit] {}

  private val needForPauseCallback = new Callback[(FiniteDuration, Int), Unit] {}

  private val needForResumeCallback = new Callback0[Unit] {}

  @BeanProperty var props: Map[String, AnyRef] = Map.empty

  @BeanProperty var topics: Set[String] = Set.empty

  @BeanProperty var pollTimeout: FiniteDuration = 1000 millis

  @BeanProperty var pauseDuration: FiniteDuration = 1000 millis

  @BeanProperty var keyDeserializer: Option[Deserializer[K]] = None

  @BeanProperty var valueDeserializer: Option[Deserializer[V]] = None

  @BeanProperty var consumerRebalanceListenerBuilder: Option[Consumer[K, V] => ConsumerRebalanceListener] = None

  @BeanProperty var useSelfAsRebalanceListener: Boolean = true

  @BeanProperty var consumerRecordsController: Option[ConsumerRecordsController[K, V]] = None

  @BeanProperty var consumptionStrategy: ConsumptionStrategy = One

  @BeanProperty var useAutoCommit: Boolean = false

  @BeanProperty var maxCommitAttempts = 3

  @BeanProperty var maxCommitAttemptBackoff: FiniteDuration = 1000 millis

  @BeanProperty var delaySingleRecord: FiniteDuration = 0 millis

  @BeanProperty var delayRecords: FiniteDuration = 0 millis

  protected var consumer: Consumer[K, V] = _

  def onPreConsume(f: () => Unit): Unit = preConsumeCallback.addCallback(f)

  def onPostConsume(f: Int => Unit): Unit = postConsumeCallback.addCallback(f)

  def onPostCommit(f: Int => Unit): Unit = postCommitCallback.addCallback(f)

  def onNeedForPauseCallback(f: ((FiniteDuration, Int)) => Unit): Unit = needForPauseCallback.addCallback(f)

  def onNeedForResumeCallback(f: () => Unit): Unit = needForResumeCallback.addCallback(f)

  def process(consumerRecords: Vector[ConsumerRecord[K, V]]): Future[ProcessResult[K, V]] = {
    getConsumerRecordsController
      .map(_.process(consumerRecords))
      .getOrElse(Future.failed(ConsumerRecordsControllerException("No Records Controller Found")))
  }

  //TODO: HANDLE AUTOCOMMIT
  override def execute(): Unit = {
    try {
      createConsumer(getProps)
      subscribe(getTopics.toList, getConsumerRebalanceListenerBuilder)

      val failed = new AtomicReference[Option[Throwable]](None)
      val commitAttempts = new AtomicInteger(getMaxCommitAttempts)

      while (getRunning) {

        try {

          preConsumeCallback.run()

          val pollTimeDuration = java.time.Duration.ofMillis(getPollTimeout.toMillis)
          val consumerRecords = consumer.poll(pollTimeDuration)
          val totalPolledCount = consumerRecords.count()

          try {
            getConsumptionStrategy match {
              case All =>
                if (totalPolledCount > 0) {
                  allFactory(consumerRecords).run()
                }
              case One =>
                val partitions = consumerRecords.partitions().asScala.toSet
                for { (partition, i) <- partitions.zipWithIndex } {
                  oneFactory(i, partition, partitions, consumerRecords).run()
                }
            }
          } finally {
            //this is in a try to guaranty its execution.
            postConsumeCallback.run(totalPolledCount)
          }

          //This is a listener on other exception for when the consumer is not paused.
          failed.getAndSet(None).foreach { e => throw e }

        } catch {
          case e: NeedForPauseException =>
            import monix.execution.Scheduler.{ global => scheduler }
            val partitions = consumer.assignment()
            consumer.pause(partitions)
            getIsPaused.set(true)
            getPausedHistory.set(getPausedHistory.get() + 1)
            val currentPauses = pauses.get()
            val pause: FiniteDuration = e.maybeDuration.getOrElse(amortizePauseDuration())
            scheduler.scheduleOnce(pause) {
              failed.set(Some(NeedForResumeException(s"Restarting after a $pause millis sleep...")))
            }
            needForPauseCallback.run((pause, currentPauses))
            logger.debug(s"NeedForPauseException: duration[{}] pause cycle[{}] partitions[{}]", pause, currentPauses, partitions.size())
          case e: NeedForResumeException =>
            val partitions = consumer.assignment()
            consumer.resume(partitions)
            getIsPaused.set(false)
            getUnPausedHistory.set(getUnPausedHistory.get() + 1)
            needForResumeCallback.run()
            logger.debug("NeedForResumeException: [{}], partitions[{}]", e.getMessage, partitions.size())
          case e: CommitTimeoutException =>
            logger.error("(Control) Commit timed out={}", e.getMessage)
            import scala.util.control.Breaks._
            breakable {
              while (true) {
                val currentAttempts = commitAttempts.getAndDecrement()
                logger.error("Trying one more time. Attempts [{}]", currentAttempts)
                if (currentAttempts == 1) {
                  throw MaxNumberOfCommitAttemptsException("Error Committing", s"$commitAttempts attempts were performed. But none worked. Escalating ...", Left(e))
                } else {
                  try {
                    new FutureHelper().delay(getMaxCommitAttemptBackoff)(e.commitFunc())
                    break()
                  } catch {
                    case _: CommitTimeoutException =>
                  }
                }
              }
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
        logger.error("Exception floor (0) ... Exception: [{}] Message: [{}]", e.getClass.getCanonicalName, Option(e.getMessage).getOrElse(""), e)
        startGracefulShutdown()
    } finally {
      if (consumer != null) consumer.close()
    }
  }

  private def amortizePauseDuration(): FiniteDuration = {
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

  @throws(classOf[ConsumerCreationException])
  def createConsumer(props: Map[String, AnyRef]): Unit = {

    logger.debug(s"Starting Processing in mode '${getConsumptionStrategy.toString}'")

    if (getKeyDeserializer.isEmpty && getValueDeserializer.isEmpty) {
      throw ConsumerCreationException("No Serializers Found", "Please set the serializers for the key and value.")
    }

    if (props.isEmpty) {
      throw ConsumerCreationException("No Properties Found", "Please, set the properties for the consumer creation.")
    }

    try {
      val kd = getKeyDeserializer.get
      val vd = getValueDeserializer.get
      val propsAsJava = props.asJava

      kd.configure(propsAsJava, true)
      vd.configure(propsAsJava, false)

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

      consumer = new KafkaConsumer[K, V](propsAsJava, kd, vd)

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
          logger.debug("Subscribing to [{}] with external rebalance listener [{}]", topics.mkString(" "), rebalancer.getClass.getCanonicalName)
          consumer.subscribe(topicsAsJava, rebalancer)
        case _ if getUseSelfAsRebalanceListener =>
          logger.debug("Subscribing to [{}] with self rebalance listener", topics.mkString(" "))
          consumer.subscribe(topicsAsJava, this)
        case _ =>
          logger.debug("Subscribing to [{}] with no rebalance listener", topics.mkString(" "))
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

  def startPolling(): Unit = start()

}

/**
  * Simple Companion Object for the Consumer Runner
  */
object ConsumerRunner {
  val version: AtomicInteger = new AtomicInteger(0)

  def fBased[K, V](f: Vector[ConsumerRecord[K, V]] => Future[ProcessResult[K, V]])(implicit executionContext: ExecutionContext): ConsumerRunner[K, V] = {
    new ConsumerRunner[K, V](name) {

      override implicit def ec: ExecutionContext = executionContext

      override def process(consumerRecords: Vector[ConsumerRecord[K, V]]): Future[ProcessResult[K, V]] = {
        f(consumerRecords)
      }
    }
  }

  def controllerBased[K, V](controller: ConsumerRecordsController[K, V])(implicit ec: ExecutionContext): ConsumerRunner[K, V] = {
    val consumer = empty[K, V]
    consumer.setConsumerRecordsController(Some(controller))
    consumer
  }

  def empty[K, V](implicit executionContext: ExecutionContext): ConsumerRunner[K, V] = {
    new ConsumerRunner[K, V](name) {
      override implicit def ec: ExecutionContext = executionContext
    }

  }

  def emptyWithMetrics[K, V](metricsSubNamespace: String)(implicit executionContext: ExecutionContext): ConsumerRunner[K, V] = {
    new ConsumerRunner[K, V](name) with WithMetrics {
      override implicit def ec: ExecutionContext = executionContext
      override def metricsSubNamespaceLabel: String = metricsSubNamespace
    }
  }

  def name: String = "consumer_runner_thread" + "_" + UUID.randomUUID()

}
