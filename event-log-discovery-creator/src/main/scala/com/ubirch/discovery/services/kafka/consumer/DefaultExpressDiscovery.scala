package com.ubirch.discovery.services.kafka.consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.discovery.models.Relation
import com.ubirch.discovery.process.RelationStrategy
import com.ubirch.discovery.util.DiscoveryJsonSupport
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.models.EventLog
import com.ubirch.services.kafka.consumer.ConsumerShutdownHook
import com.ubirch.services.kafka.producer.ProducerShutdownHook
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.URLsHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

@Singleton
class DefaultExpressDiscovery @Inject() (val config: Config, lifecycle: Lifecycle)(implicit ec: ExecutionContext)
  extends ExpressKafka[String, String, Unit] with LazyLogging {

  final val composed = getEventLog _ andThen getRelations andThen getRelationsAsJson

  def metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  def consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  def consumerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ConsumerConfPaths.BOOTSTRAP_SERVERS))

  def consumerGroupId: String = config.getString(ConsumerConfPaths.GROUP_ID_PATH)

  def consumerMaxPollRecords: Int = config.getInt(ConsumerConfPaths.MAX_POLL_RECORDS)

  def consumerGracefulTimeout: Int = config.getInt(ConsumerConfPaths.GRACEFUL_TIMEOUT_PATH)

  def keyDeserializer: Deserializer[String] = new StringDeserializer

  def valueDeserializer: Deserializer[String] = new StringDeserializer

  def producerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ProducerConfPaths.BOOTSTRAP_SERVERS))

  def lingerMs: Int = config.getInt(ProducerConfPaths.LINGER_MS)

  def keySerializer: Serializer[String] = new StringSerializer

  def valueSerializer: Serializer[String] = new StringSerializer

  def getEventLog(consumerRecord: ConsumerRecord[String, String]) = DiscoveryJsonSupport.FromString[EventLog](consumerRecord.value()).get

  def getRelations(eventLog: EventLog) = {
    val rs = RelationStrategy.getStrategy(eventLog).create
    if(rs.isEmpty){
      logger.warn("No relations created. It is possible that the incoming data doesn't have the needed values.")
    }
    rs
  }

  def getRelationsAsJson(relations: Seq[Relation]) = DiscoveryJsonSupport.ToJson[Seq[Relation]](relations).toString

  def process(consumerRecords: Vector[ConsumerRecord[String, String]]): Future[Unit] = {
    consumerRecords.foreach(x => run(x).recover {
      case NonFatal(e) =>
        logger.error("Error Creating Relation, ", e)
      case e => throw e
    })
    Future.unit
  }

  def run(consumerRecord: ConsumerRecord[String, String]) = {
    Future(composed(consumerRecord)).flatMap { json =>
      send(producerTopic, json)
    }
  }

  def producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

  lifecycle.addStopHooks(
    ConsumerShutdownHook.hookFunc(consumerGracefulTimeout, consumption),
    ProducerShutdownHook.hookFunc(production)
  )

}
