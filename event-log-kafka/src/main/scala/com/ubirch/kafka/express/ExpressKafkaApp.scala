package com.ubirch.kafka.express

import java.util.UUID
import java.util.concurrent.{ CountDownLatch, TimeUnit }

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer._
import com.ubirch.kafka.producer.{ ProducerBasicConfigs, ProducerRunner, WithProducerShutdownHook, WithSerializer }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait ExpressConsumer[K, V] extends ConsumerBasicConfigs with WithDeserializers[K, V] {

  def metricsSubNamespace: String

  def maxTimeAggregationSeconds: Long

  def controller: ConsumerRecordsController[K, V]

  def consumption: ConsumerRunner[K, V]

}

trait ExpressProducer[K, V] extends ProducerBasicConfigs with WithSerializer[K, V] {

  def production: ProducerRunner[K, V]

  def send(topic: String, value: V): Future[RecordMetadata] = production.send(new ProducerRecord[K, V](topic, value))

}

trait WithShutdownHook extends WithConsumerShutdownHook with WithProducerShutdownHook {
  ek: ExpressKafkaApp[_, _, _] =>

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      val countDownLatch = new CountDownLatch(1)
      (for {
        _ <- hookFunc(consumerGracefulTimeout, consumption)()
        _ <- hookFunc(production)()
      } yield ())
        .onComplete {
          case Success(_) => countDownLatch.countDown()
          case Failure(e) =>
            logger.error("Error running jvm hook={}", e.getMessage)
            countDownLatch.countDown()
        }

      val res = countDownLatch.await(5000, TimeUnit.SECONDS) //Waiting 5 secs
      if (!res) logger.warn("Taking too much time shutting down :(  ..")
      else logger.info("Bye bye, see you later...")
    }
  })
}

trait WithMain {
  ek: ExpressKafkaApp[_, _, _] =>

  def main(args: Array[String]): Unit = {
    start()
    val cd = new CountDownLatch(1)
    cd.await()
  }

}

object ConfigBase {
  lazy val conf: Config = ConfigFactory.load()
}

trait ConfigBase {
  def conf: Config = ConfigBase.conf
}

trait ExpressKafka[K, V, R] extends ExpressConsumer[K, V] with ExpressProducer[K, V] {
  thiz =>

  implicit def ec: ExecutionContext

  lazy val controller: ConsumerRecordsController[K, V] = new ConsumerRecordsController[K, V] {

    def simpleProcessResult(result: R, consumerRecord: Vector[ConsumerRecord[K, V]]): ProcessResult[K, V] = new ProcessResult[K, V] {
      override val id: UUID = UUID.randomUUID()
      override val consumerRecords: Vector[ConsumerRecord[K, V]] = consumerRecord
    }

    override type A = ProcessResult[K, V]
    override def process(consumerRecords: Vector[ConsumerRecord[K, V]]): Future[ProcessResult[K, V]] = {
      thiz.process.invoke(consumerRecords).map(x => simpleProcessResult(x, consumerRecords))
    }
  }

  lazy val production: ProducerRunner[K, V] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))

  lazy val consumption: ConsumerRunner[K, V] = {
    val impl = ConsumerRunner.emptyWithMetrics[K, V](metricsSubNamespace)
    impl.setUseAutoCommit(false)
    impl.setTopics(consumerTopics)
    impl.setProps(consumerConfigs)
    impl.setKeyDeserializer(Some(keyDeserializer))
    impl.setValueDeserializer(Some(valueDeserializer))
    impl.setConsumerRecordsController(Some(controller))
    impl.setConsumptionStrategy(All)
    impl.setMaxTimeAggregationSeconds(maxTimeAggregationSeconds)
    impl
  }

  trait Process {
    def invoke(consumerRecords: Vector[ConsumerRecord[K, V]]): Future[R]
  }

  object Process {
    def apply(p: Vector[ConsumerRecord[K, V]] => R): Process = (consumerRecords: Vector[ConsumerRecord[K, V]]) => Future(p(consumerRecords))
    def async(p: Vector[ConsumerRecord[K, V]] => Future[R]): Process = (consumerRecords: Vector[ConsumerRecord[K, V]]) => p(consumerRecords)
  }

  def process: Process

  def start(): Unit = consumption.startPolling()

}

trait ExpressKafkaApp[K, V, R]
  extends ExpressKafka[K, V, R]
  with WithShutdownHook
  with WithMain
  with ConfigBase
  with LazyLogging

