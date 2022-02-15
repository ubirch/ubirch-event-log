package com.ubirch.encoder.process

import java.io.ByteArrayInputStream
import java.util.{ Date, UUID }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.encoder.models.{ AcctEvent, BlockchainResponse, PublicKey }
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.services.metrics.DefaultEncodingsCounter
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.ConsumerRecordsController
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog, LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.kafka.EnrichedConsumerRecord.enrichedConsumerRecord
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._

import javax.inject._
import monix.eval.Task
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.json4s.JValue
import org.json4s.JsonAST.JNull
import org.json4s.jackson.JsonMethods._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
  * Represents a pipeline executor for the encoding process.
  * @param reporter Represents a reporter for errors
  * @param encodingsCounter Represents a Prometheus counter for the number of encodings
  * @param successCounter Represents a Prometheus counter for the number of successes
  * @param failureCounter Represents a Prometheus counter for the number of failures
  * @param config Represents a config object
  * @param stringProducer Represents a kafka string producer
  * @param ec Represents an execution context
  */
@Singleton
class EncoderExecutor @Inject() (
    reporter: Reporter,
    @Named(DefaultEncodingsCounter.name) encodingsCounter: Counter,
    @Named(DefaultSuccessCounter.name) successCounter: Counter,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config,
    stringProducer: StringProducer
)(implicit ec: ExecutionContext) extends ConsumerRecordsController[String, Array[Byte]]
  with ProducerConfPaths
  with LazyLogging {

  import LookupKey._
  import reporter.Types._

  override type A = EncoderPipeData

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)
  lazy val sign: Boolean = config.getBoolean("eventLog.sign")
  lazy val topic = config.getStringAsOption(TOPIC_PATH).getOrElse(throw new Exception("No Publishing Topic configured"))
  lazy val scheduler = monix.execution.Scheduler(ec)

  final val CUSTOMER_ID_FIELD = "customerId"

  logger.info("publish_topic=[{}]", topic)

  override def process(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]]): Future[EncoderPipeData] = {
    consumerRecords.map { x =>
      run(x).runOnComplete {
        case Success(_) =>
          successCounter.counter.labels(metricsSubNamespace).inc()
        case Failure(e: EncodingException) =>
          logger.error("EncodingException: " + e.getMessage)
          failureCounter.counter.labels(metricsSubNamespace).inc()
          val value = e.pipeData.jValues.headOption.map(x => compact(x)).getOrElse("No Value")
          reporter.report(Error(id = UUIDHelper.randomUUID, message = e.getMessage, exceptionName = e.name, value = value))
        case Failure(e) =>
          logger.error("EncodingException (other): " + e.getMessage)
          failureCounter.counter.labels(metricsSubNamespace).inc()
          reporter.report(Error(id = UUIDHelper.randomUUID, message = e.getMessage, exceptionName = e.getClass.getName, value = e.getMessage))

      }(scheduler)
    }

    Future.successful(EncoderPipeData(consumerRecords, Vector.empty))
  }

  private def run(x: ConsumerRecord[String, Array[Byte]]): Task[List[RecordMetadata]] = {
    Task.defer {
      var jValue: JValue = JNull
      try {
        val bytes = new ByteArrayInputStream(x.value())
        jValue = parse(bytes)
        val ldp = EncoderPipeData(Vector(x), Vector(jValue))
        encode(ldp).map { el =>
          val _el = if (sign) el.sign(config) else el
          ProducerRecordHelper.toRecord(topic, _el.id, _el.toJson, Map.empty)
        } match {
          case Nil => throw EncodingException("Error in the Encoding Process: No PR to send", EncoderPipeData(Vector(x), Vector(jValue)))
          case prs => Task.sequence(prs.map(pr => Task.fromFuture(stringProducer.send(pr))))
        }
      } catch {
        case e: Exception =>
          throw EncodingException("Error in the Encoding Process: " + e.getMessage, EncoderPipeData(Vector(x), Vector(jValue)))
      }
    }
  }

  private val UPP: PartialFunction[EncoderPipeData, List[EventLog]] = {

    case encoderPipeData @ EncoderPipeData(_, Vector(jv)) if Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get).isSuccess =>
      // there is a guarantee that this json has the format of MessageEnvelope
      val messageEnvelope = EncoderJsonSupport.FromJson[MessageEnvelope](jv).get
      val _customerId = UPPHelper.getFieldFromContext(messageEnvelope, CUSTOMER_ID_FIELD, encoderPipeData)

      def onUPPCat: Option[EventLog] = {
        encodingsCounter.counter.labels(metricsSubNamespace, Values.UPP_CATEGORY).inc()
        (for {
          customerId <- _customerId
          payload <- Try(EncoderJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket).get)
          payloadHash <- UPPHelper.createPayloadHash(messageEnvelope, encoderPipeData)
          signatureLookupKey <- UPPHelper.createSignatureLookupKey(payloadHash, messageEnvelope, encoderPipeData, Values.UPP_CATEGORY)
          deviceLookupKey <- UPPHelper.createDeviceLookupKey(payloadHash, messageEnvelope, encoderPipeData, Values.UPP_CATEGORY)
          chainLookupKey <- UPPHelper.createChainLookupKey(payloadHash, messageEnvelope, encoderPipeData, Values.UPP_CATEGORY)
        } yield {
          Some(EventLog("upp-event-log-entry", Values.UPP_CATEGORY, payload)
            .withLookupKeys(signatureLookupKey ++ chainLookupKey ++ deviceLookupKey)
            .withCustomerId(customerId)
            .withRandomNonce
            .withNewId(payloadHash)
            .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM))
        }).get
      }

      def onUpdateCat(updateCategory: String): Option[EventLog] = {
        encodingsCounter.counter.labels(metricsSubNamespace, updateCategory).inc()
        (for {
          customerId <- _customerId
          payload <- Try(EncoderJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket).get)
          payloadHash <- UPPHelper.createPayloadHash(messageEnvelope, encoderPipeData)
        } yield {
          Some(EventLog("upp-update-event-log-entry", updateCategory, payload)
            .withCustomerId(customerId)
            .withRandomNonce
            .withNewId(payloadHash)
            .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM))
        }).get
      }

      def acctEvent: Option[EventLog] = {
        (for {
          customerId <- _customerId
          payload <- Try(EncoderJsonSupport.ToJson[AcctEvent](AcctEvent(
            UUIDHelper.randomUUID,
            ownerId = UUID.fromString(customerId),
            identityId = messageEnvelope.ubirchPacket.getUUID,
            category = "anchoring",
            subCategory = None,
            externalId = None,
            token = encoderPipeData.consumerRecords.headOption.flatMap(cr => cr.findHeader("X-Ubirch-DeviceInfo-Token")),
            occurredAt = new Date()
          )).get)
        } yield {
          Some(EventLog("upp-acct-event-log-entry", Values.ACCT_CATEGORY, payload)
            .withCustomerId(customerId)
            .withRandomNonce
            .withNewId
            .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM))
        }).get
      }

      val uppF = UPPHelper.getCategory(messageEnvelope).flatMap {
        case Values.UPP_CATEGORY => onUPPCat
        case updateCategory @ (
          Values.UPP_DISABLE_CATEGORY |
          Values.UPP_ENABLE_CATEGORY |
          Values.UPP_DELETE_CATEGORY) =>
          onUpdateCat(updateCategory)
      }.toList

      val acctF = UPPHelper.getCategory(messageEnvelope).flatMap {
        case Values.UPP_CATEGORY => acctEvent
        case _ => None
      }.toList

      uppF ++ acctF
  }

  private val PublicBlockchain: PartialFunction[EncoderPipeData, List[EventLog]] = {
    case EncoderPipeData(_, Vector(jv)) if Try(EncoderJsonSupport.FromJson[BlockchainResponse](jv).get).isSuccess =>
      (for {
        blockchainResponse <- Try(EncoderJsonSupport.FromJson[BlockchainResponse](jv).get)
        _ = encodingsCounter.counter.labels(metricsSubNamespace, Values.PUBLIC_CHAIN_CATEGORY).inc()
        //The category of the tx event log has to be the name of the lookup key.
        eventLog = EventLog("EventLogFromConsumerRecord", blockchainResponse.category, jv)
          .withCustomerId(Values.UBIRCH)
          .withNewId(blockchainResponse.txid)
          .withLookupKeys(Seq(
            LookupKey(
              name = blockchainResponse.category,
              category = Values.PUBLIC_CHAIN_CATEGORY,
              key = blockchainResponse.txid.asKey,
              value = Seq(blockchainResponse.message.asValue)
            ).categoryAsKeyLabel
              .addValueLabelForAll(Values.MASTER_TREE_CATEGORY)
          ))
          .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM)
      } yield {
        Some(eventLog)
      }).get.toList
  }

  private val PublicKey: PartialFunction[EncoderPipeData, List[EventLog]] = {
    case EncoderPipeData(_, Vector(jv)) if Try(EncoderJsonSupport.FromJson[PublicKey](jv).get).isSuccess =>
      (for {
        publicKey <- Try(EncoderJsonSupport.FromJson[PublicKey](jv).get)
        _ = encodingsCounter.counter.labels(metricsSubNamespace, Values.PUBLIC_CHAIN_CATEGORY).inc()
        eventLog = EventLog("EventLogFromConsumerRecord", Values.PUB_KEY_CATEGORY, jv)
          .withCustomerId(publicKey.id)
          .withNewId(publicKey.publicKey)
          .withLookupKeys(Seq(
            LookupKey(
              name = Values.PUB_KEY_CATEGORY,
              category = Values.PUB_KEY_CATEGORY,
              key = publicKey.publicKey.asKey,
              value = Seq(publicKey.id.asValue)
            ).categoryAsKeyLabel
              .addValueLabelForAll(Values.PUB_KEY_CATEGORY)
          ))
          .withRandomNonce
          .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM)
      } yield {
        Some(eventLog)
      }).get.toList
  }

  private val OrElse: PartialFunction[EncoderPipeData, List[EventLog]] = {
    case encoderPipeData @ EncoderPipeData(_, Vector(jv)) =>
      encodingsCounter.counter.labels(metricsSubNamespace, Values.UNKNOWN_CATEGORY).inc()
      val data = compact(jv)
      logger.error("No supported: " + data)
      throw EventLogFromConsumerRecordException(s"$data", encoderPipeData)
  }

  private val encode: PartialFunction[EncoderPipeData, List[EventLog]] =
    UPP orElse PublicBlockchain orElse PublicKey orElse OrElse
}
