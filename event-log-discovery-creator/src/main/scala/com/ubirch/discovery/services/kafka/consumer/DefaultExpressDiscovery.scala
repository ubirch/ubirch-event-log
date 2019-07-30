package com.ubirch.discovery.services.kafka.consumer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.services.kafka.consumer.ConsumerShutdownHook
import com.ubirch.services.kafka.producer.ProducerShutdownHook
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.URLsHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

import scala.concurrent.Future

@Singleton
class DefaultExpressDiscovery @Inject() (val config: Config, lifecycle: Lifecycle)
  extends ExpressKafka[String, String, Unit] {

  def consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  def consumerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ConsumerConfPaths.BOOTSTRAP_SERVERS))

  def consumerGroupId: String = config.getString(ConsumerConfPaths.GROUP_ID_PATH)

  def consumerMaxPollRecords: Int = config.getInt(ConsumerConfPaths.MAX_POLL_RECORDS)

  def consumerGracefulTimeout: Int = config.getInt(ConsumerConfPaths.GRACEFUL_TIMEOUT_PATH)

  def keyDeserializer: Deserializer[String] = new StringDeserializer

  def valueDeserializer: Deserializer[String] = new StringDeserializer

  def producerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ProducerConfPaths.BOOTSTRAP_SERVERS))

  def keySerializer: Serializer[String] = new StringSerializer

  def valueSerializer: Serializer[String] = new StringSerializer

  def process(consumerRecords: Vector[ConsumerRecord[String, String]]): Future[Unit] = {
    Future.successful(println(consumerRecords))
  }

  lifecycle.addStopHooks(
    ConsumerShutdownHook.hookFunc(consumerGracefulTimeout, consumption),
    ProducerShutdownHook.hookFunc(production)
  )

}
