package com.ubirch.discovery.services.kafka.consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.discovery.models.Relation
import com.ubirch.discovery.process.RelationStrategyImpl
import com.ubirch.discovery.util.DiscoveryJsonSupport
import com.ubirch.discovery.util.Exceptions.{ ParsingError, StrategyException }
import com.ubirch.kafka.consumer.ConsumerShutdownHook
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.kafka.producer.ProducerShutdownHook
import com.ubirch.models.EnrichedError._
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.services.kafka.consumer.ConsumerCreator
import com.ubirch.services.kafka.producer.ProducerCreator
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringDeserializer, StringSerializer }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import scala.util.control.NonFatal

abstract class DefaultExpressDiscoveryBase(val config: Config, lifecycle: Lifecycle)
  extends ExpressKafka[String, String, Unit]
  with ConsumerCreator
  with ProducerCreator
  with LazyLogging {

  def keyDeserializer: Deserializer[String] = new StringDeserializer

  def valueDeserializer: Deserializer[String] = new StringDeserializer

  def keySerializer: Serializer[String] = new StringSerializer

  def valueSerializer: Serializer[String] = new StringSerializer

  override def metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  def producerTopic: String = config.getString(ProducerConfPaths.TOPIC_PATH)

  def errorTopic: String = config.getString(ProducerConfPaths.ERROR_TOPIC_PATH)

  def consumerGroupIdOnEmpty: String = "DefaultExpressDiscoveryBase"

  lifecycle.addStopHooks(
    ConsumerShutdownHook.hookFunc(consumerGracefulTimeout, consumption),
    ProducerShutdownHook.hookFunc(production)
  )

}

@Singleton
class DefaultExpressDiscovery @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    relationStrategyImpl: RelationStrategyImpl
)(implicit ec: ExecutionContext)
  extends DefaultExpressDiscoveryBase(config, lifecycle) with LazyLogging {

  final val composed = getEventLog _ andThen getRelations andThen getRelationsAsJson

  def getEventLog(consumerRecord: ConsumerRecord[String, String]) = {
    if (consumerRecord.value().isEmpty) {
      throw ParsingError("Error parsing", "Empty Value", consumerRecord.value())
    }
    Try(DiscoveryJsonSupport.FromString[EventLog](consumerRecord.value()).get)
      .recover {
        case e => throw ParsingError("Error parsing", e.getMessage, consumerRecord.value())
      }.get
  }

  def getRelations(eventLog: EventLog) = {
    val rs = relationStrategyImpl.getStrategy(eventLog).create
    if (rs.isEmpty) {
      logger.warn("No relations created. It is possible that the incoming data doesn't have the needed values. EventLog {} ", eventLog.toJson)
    }
    rs
  }

  def getRelationsAsJson(relations: Seq[Relation]) = {
    Try(DiscoveryJsonSupport.ToJson[Seq[Relation]](relations).toString)
      .recover {
        case e => throw ParsingError("Error parsing", e.getMessage, relations.toString())
      }.get
  }

  override def process: Process = Process.async { consumerRecords =>

    val res = consumerRecords.map { x =>
      Future(composed(x)).flatMap { json =>
        send(producerTopic, json)
      } recoverWith {
        case NonFatal(e: StrategyException) =>
          logger.error("Error Creating Relation (1): ", e)
          logger.error("Error Creating Relation (1.1): {}", x.value())
          send(errorTopic, Error(e.eventLog.id, e.message, e.getClass.getName, e.eventLog.toJson).toEventLog(errorTopic).toJson)
        case NonFatal(e) =>
          logger.error("Error Creating Relation (2): ", e)
          send(errorTopic, Error(UUIDHelper.randomUUID, e.getMessage, e.getClass.getName).toEventLog(errorTopic).toJson)
        case e =>
          logger.error("Fatal Error Encountered: ", e)
          send(errorTopic, Error(UUIDHelper.randomUUID, e.getMessage, e.getClass.getName).toEventLog(errorTopic).toJson)
          throw e
      }
    }

    Future.sequence(res).map(_ => ())

  }

}
