package com.ubirch.kafka.express

import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.{ ConsumerBasicConfigs, ConsumerRecordsController, ConsumerRunner, ProcessResult }
import com.ubirch.kafka.producer.{ ProducerBasicConfigs, ProducerRunner }
import com.ubirch.util.FutureHelper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Controller[K, V] {

  thiz =>

  val controller = new ConsumerRecordsController[K, V] {

    def simpleProcessResult(consumerRecord: Vector[ConsumerRecord[K, V]]): ProcessResult[K, V] = new ProcessResult[K, V] {
      override val id: UUID = UUID.randomUUID()
      override val consumerRecords: Vector[ConsumerRecord[K, V]] = consumerRecord
    }

    override type A = ProcessResult[K, V]

    override def process(consumerRecord: Vector[ConsumerRecord[K, V]]): Future[ProcessResult[K, V]] = {
      thiz.process(consumerRecord)
      Future.successful(simpleProcessResult(consumerRecord))
    }
  }

  def process(consumerRecords: Vector[ConsumerRecord[K, V]]): Unit

}

trait ExpressConsumer[K, V] extends ConsumerBasicConfigs[K, V] with Controller[K, V] {
  lazy val consumption = {
    val consumerImp = ConsumerRunner.empty[K, V]
    consumerImp.setUseAutoCommit(false)
    consumerImp.setTopics(consumerTopics)
    consumerImp.setProps(consumerConfigs)
    consumerImp.setKeyDeserializer(Some(keyDeserializer))
    consumerImp.setValueDeserializer(Some(valueDeserializer))
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }
}

trait ExpressProducer[K, V] extends ProducerBasicConfigs[K, V] {

  lazy val production = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))
  lazy val futureHelper = new FutureHelper()

  def send(topic: String, value: V): Future[RecordMetadata] = {
    val javaFutureSend = production.getProducerOrCreate.send(new ProducerRecord[K, V](topic, value))
    futureHelper.fromJavaFuture(javaFutureSend)
  }

}

trait WithShutdownHook[K, V] {
  ek: ExpressKafkaApp[K, V] =>

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {

      logger.info("Shutting down Consumer: " + consumption.getName)
      consumption.shutdown(consumerGracefulTimeout, java.util.concurrent.TimeUnit.SECONDS)

      logger.info("Shutting down Producer")
      production.getProducerAsOpt.foreach(_.close())

      Thread.sleep(5000) //Waiting 5 secs
      logger.info("Bye bye, see you later...")
    }
  })
}

trait WithMain[K, V] {
  ek: ExpressKafkaApp[K, V] =>

  def main(args: Array[String]): Unit = {
    consumption.startPolling()
    val cd = new CountDownLatch(1)
    cd.await()
  }

}

trait ConfigBase {
  def conf: Config = ConfigFactory.load()
}

trait ExpressKafka[K, V] extends ExpressConsumer[K, V] with ExpressProducer[K, V]

trait ExpressKafkaApp[K, V]
  extends ExpressKafka[K, V]
  with WithShutdownHook[K, V]
  with WithMain[K, V]
  with ConfigBase
  with LazyLogging

