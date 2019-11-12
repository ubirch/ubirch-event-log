package com.ubirch.services.healthcheck

import com.typesafe.config.Config
import com.ubirch.ConfPaths.HealthCheckConfPaths
import com.ubirch.kafka.consumer.{BytesConsumer, ConsumerRunner, StringConsumer}
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.producer.{BytesProducer, ProducerRunner, StringProducer}
import com.ubirch.niomon.healthcheck.HealthCheckServer.CheckerFn
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import com.ubirch.util.InjectorHelper
import javax.inject.{Inject, Singleton}
import org.apache.kafka.clients.producer.Producer

import scala.concurrent.ExecutionContext
import scala.util.Success

@Singleton
class HealthCheck @Inject() (config: Config) extends HealthCheckConfPaths {

  val server = new HealthCheckServer(Map(), Map())
  var anyKafkaProducer: Option[Either[Producer[_, _], ProducerRunner[_, _]]] = None

  def init(injectorHelper: InjectorHelper): Unit = {
    if (!config.getBoolean(ENABLED)) return

    server.setLivenessCheck(Checks.process())
    server.setReadinessCheck(Checks.process())

    addChecksForGuiceManagedKafkaClientRunners(injectorHelper)
    addChecksForExpressKafka(injectorHelper)
    addReachabilityChecksIfAnyProducerFound()

    server.run(config.getInt(PORT))
  }

  def addChecksForProducerRunner(name: String, producerRunner: ProducerRunner[_, _]): Unit = {
    server.setReadinessCheck(name) { implicit ec =>
      producerRunner.getProducerAsOpt match {
        case Some(producer) => Checks.kafka(name, producer, connectionCountMustBeNonZero = false)._2(ec)
        case None => Checks.notInitialized(name)._2(ec)
      }
    }
  }

  def addChecksForConsumerRunner(name: String, consumerRunner: ConsumerRunner[_, _]): Unit = {
    server.setReadinessCheck(name) { implicit ec =>
      consumerRunner.getConsumerAsOpt match {
        case Some(producer) => Checks.kafka(name, producer, connectionCountMustBeNonZero = false)._2(ec)
        case None => Checks.notInitialized(name)._2(ec)
      }
    }
  }


  def addChecksForGuiceManagedKafkaClientRunners(injectorHelper: InjectorHelper): Unit = {
    val kafkaProducers: Seq[ProducerRunner[_, _]] = Seq(
      injectorHelper.getAsTry[BytesProducer],
      injectorHelper.getAsTry[StringProducer]
    ).collect { case Success(producerRunner) => producerRunner }

    val kafkaConsumers: Seq[ConsumerRunner[_, _]] = Seq(
      injectorHelper.getAsTry[BytesConsumer],
      injectorHelper.getAsTry[StringConsumer]
    ).collect { case Success(consumerRunner) => consumerRunner }

    kafkaProducers.zipWithIndex.foreach { case (producerRunner, idx) =>
      val name = s"kafka-producer-$idx"
     addChecksForProducerRunner(name, producerRunner)
    }

    kafkaConsumers.zipWithIndex.foreach { case (consumerRunner, idx) =>
      val name = s"kafka-consumer-$idx"
      addChecksForConsumerRunner(name, consumerRunner)
    }

    anyKafkaProducer = kafkaProducers.headOption.map(Right(_))
  }

  def addChecksForExpressKafka(injectorHelper: InjectorHelper): Unit = {
    injectorHelper.getAsTry[ExpressKafka[_, _, _]].foreach { ek =>
      val producerRunner = ek.production
      val consumerRunner = ek.consumption
      addChecksForProducerRunner("express-kafka-producer", producerRunner)
      addChecksForConsumerRunner("express-kafka-consumer", consumerRunner)

      anyKafkaProducer = Some(Right(producerRunner))
    }
  }

  def addReachabilityChecksIfAnyProducerFound(): Unit = {
    val checkName = "kafka-nodes-reachable"

    def checkReachabilityForRunner(runner: ProducerRunner[_, _]): CheckerFn = { ec: ExecutionContext =>
      runner.getProducerAsOpt match {
        case Some(producer) => Checks.kafkaNodesReachable(producer)._2(ec)
        case None =>
          Checks.notInitialized(checkName)._2(ec)
      }
    }

    anyKafkaProducer.foreach { producerOrProducerRunner =>
      val check = producerOrProducerRunner match {
        case Left(producer) =>
          Checks.kafkaNodesReachable(producer)._2
        case Right(runner) =>
          checkReachabilityForRunner(runner)
      }

      server.setReadinessCheck(checkName)(check)
      server.setLivenessCheck(checkName)(check)
    }
  }
}
