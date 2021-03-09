package com.ubirch.encoder.process

import java.io.ByteArrayInputStream

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.encoder.models.{ BlockchainResponse, PublicKey }
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.services.metrics.DefaultEncodingsCounter
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.EncoderJsonSupport._
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.ConsumerRecordsController
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog, LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
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

  def run(x: ConsumerRecord[String, Array[Byte]]): Task[RecordMetadata] = {
    Task.defer {
      var jValue: JValue = JNull
      try {
        val bytes = new ByteArrayInputStream(x.value())
        jValue = parse(bytes)
        val ldp = EncoderPipeData(Vector(x), Vector(jValue))
        val pr = encode(ldp).map { el =>
          val _el = if (sign) el.sign(config) else el
          ProducerRecordHelper.toRecord(topic, _el.id, _el.toJson, Map.empty)
        }.getOrElse(throw EncodingException("Error in the Encoding Process: No PR to send", EncoderPipeData(Vector(x), Vector(jValue))))
        Task.fromFuture(stringProducer.send(pr))
      } catch {
        case e: Exception =>
          throw EncodingException("Error in the Encoding Process: " + e.getMessage, EncoderPipeData(Vector(x), Vector(jValue)))
      }
    }
  }

  val UPP: PartialFunction[EncoderPipeData, Option[EventLog]] = {

    case encoderPipeData @ EncoderPipeData(_, Vector(jv)) if Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get).isSuccess =>

      (for {

        messageEnvelope <- Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get)

        _ = encodingsCounter.counter.labels(metricsSubNamespace, Values.UPP_CATEGORY).inc()

        customerId <- Try((messageEnvelope.context \\ CUSTOMER_ID_FIELD).extract[String])
          .filter(_.nonEmpty)
          .recoverWith {
            case _ => throw EventLogFromConsumerRecordException("No CustomerId found", encoderPipeData)
          }

        payload <- Try(EncoderJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket).get)

        eventLog <- Try(messageEnvelope)
          .map(_.ubirchPacket)
          .filter(_.getHint == 0)
          .map { _ =>

            val payloadJsNode = Option(messageEnvelope)
              .flatMap(x => Option(x.ubirchPacket))
              .flatMap(x => Option(x.getPayload))
              .getOrElse(throw EventLogFromConsumerRecordException("Payload not found or is empty", encoderPipeData))

            val payloadHash: String = fromJsonNode(payloadJsNode)
              .extractOpt[String]
              .filter(_.nonEmpty)
              .map { x =>
                (Try(org.bouncycastle.util.encoders.Base64.decode(x)), x)
              } match {
                //We want to ensure that the payload is OK
                case Some((Success(value), s)) if value.length <= 100 => s
                case Some((Success(_), s)) =>
                  throw EventLogFromConsumerRecordException("Error building payload | Payload length is not valid: " + s, encoderPipeData)
                case Some((Failure(_), s)) =>
                  throw EventLogFromConsumerRecordException("Error building payload | Payload is not valid: " + s, encoderPipeData)
                case None =>
                  throw EventLogFromConsumerRecordException("Error building payload | Payload is empty", encoderPipeData)
              }

            val maybeSignature = Option(messageEnvelope.ubirchPacket)
              .flatMap(x => Option(x.getSignature))
              .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
              .flatMap {
                case Success(value) if value.nonEmpty => Some(value)
                case Success(_) => None
                case Failure(e) =>
                  logger.error("Error Parsing Into Event Log [Signature]: {}", e.getMessage)
                  throw EventLogFromConsumerRecordException(s"Error parsing signature [${e.getMessage}] ", encoderPipeData)
              }

            val signatureLookupKey = maybeSignature.map { x =>
              LookupKey(
                name = Values.SIGNATURE,
                category = Values.UPP_CATEGORY,
                key = payloadHash.asKey,
                value = Seq(x.asValue)
              ).categoryAsKeyLabel
                .nameAsValueLabelForAll
            }.toSeq

            val maybeDevice = Option(messageEnvelope.ubirchPacket)
              .flatMap(x => Option(x).map(_.getUUID))
              .map(x => Try(x.toString))
              .flatMap {
                case Success(value) if value.nonEmpty => Some(value)
                case Success(_) => None
                case Failure(e) =>
                  logger.error("Error Parsing Into Event Log [deviceId]: {}", e.getMessage)
                  throw EventLogFromConsumerRecordException(s"Error parsing deviceId [${e.getMessage}] ", encoderPipeData)
              }

            val deviceLookupKey = maybeDevice.map { x =>
              LookupKey(
                name = Values.DEVICE_ID,
                category = Values.DEVICE_CATEGORY,
                key = x.asKey,
                value = Seq(payloadHash.asValue)
              ).categoryAsKeyLabel
                .addValueLabelForAll(Values.UPP_CATEGORY)
            }.toSeq

            val maybeChain = Option(messageEnvelope.ubirchPacket)
              .flatMap(x => Option(x.getChain))
              .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
              .flatMap {
                case Success(value) if value.nonEmpty => Some(value)
                case Success(_) => None
                case Failure(e) =>
                  logger.error("Error Parsing Into Event Log [Chain]: {}", e.getMessage)
                  throw EventLogFromConsumerRecordException(s"Error parsing chain [${e.getMessage}] ", encoderPipeData)
              }

            val chainLookupKey = maybeChain.map { x =>
              LookupKey(
                name = Values.UPP_CHAIN,
                category = Values.CHAIN_CATEGORY,
                key = payloadHash.asKey,
                value = Seq(x.asValue)
              ).withKeyLabel(Values.UPP_CATEGORY)
                .categoryAsValueLabelForAll
            }.toSeq

            EventLog("upp-event-log-entry", Values.UPP_CATEGORY, payload)
              .withLookupKeys(signatureLookupKey ++ chainLookupKey ++ deviceLookupKey)
              .withCustomerId(customerId)
              .withRandomNonce
              .withNewId(payloadHash)
              .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM)

          }

      } yield {
        Option(eventLog)
      }).get
  }

  val UPP_UPDATE: PartialFunction[EncoderPipeData, Option[EventLog]] = {

    case encoderPipeData @ EncoderPipeData(_, Vector(jv)) if Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get).isSuccess =>

      def getCategory(hint: Int): Try[String] = hint match {
        case 250 => Success(Values.UPP_DISABLE_CATEGORY)
        case 251 => Success(Values.UPP_ENABLE_CATEGORY)
        case 252 => Success(Values.UPP_DELETE_CATEGORY)
        case _ => Failure(EventLogFromConsumerRecordException("No hint match found", encoderPipeData))
      }

      (for {

        messageEnvelope <- Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get)
        category <- getCategory(messageEnvelope.ubirchPacket.getHint) //TODO: Make access to hint safer for NPE.
        _ = encodingsCounter.counter.labels(metricsSubNamespace, category).inc()

        customerId <- Try((messageEnvelope.context \\ CUSTOMER_ID_FIELD).extract[String])
          .filter(_.nonEmpty)
          .recoverWith {
            case _ => throw EventLogFromConsumerRecordException("No CustomerId found", encoderPipeData)
          }

        payload <- Try(EncoderJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket).get)

        eventLog <- Try(messageEnvelope)
          .map(_.ubirchPacket)
          .map { _ =>

            val payloadJsNode = Option(messageEnvelope)
              .flatMap(x => Option(x.ubirchPacket))
              .flatMap(x => Option(x.getPayload))
              .getOrElse(throw EventLogFromConsumerRecordException("Payload not found or is empty", encoderPipeData))

            val payloadHash = fromJsonNode(payloadJsNode)
              .extractOpt[String]
              .filter(_.nonEmpty)
              .getOrElse {
                throw EventLogFromConsumerRecordException("Error building payload", encoderPipeData)
              }

            EventLog("upp-update-event-log-entry", category, payload)
              .withCustomerId(customerId)
              .withRandomNonce
              .withNewId(payloadHash)
              .addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM)

          }

      } yield {
        Option(eventLog)
      }).get
  }

  val PublicBlockchain: PartialFunction[EncoderPipeData, Option[EventLog]] = {
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
        Option(eventLog)
      }).get
  }

  val PublicKey: PartialFunction[EncoderPipeData, Option[EventLog]] = {
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
        Option(eventLog)
      }).get
  }

  val OrElse: PartialFunction[EncoderPipeData, Option[EventLog]] = {
    case encoderPipeData @ EncoderPipeData(_, Vector(jv)) =>
      encodingsCounter.counter.labels(metricsSubNamespace, Values.UNKNOWN_CATEGORY).inc()
      val data = compact(jv)
      logger.error("No supported: " + data)
      throw EventLogFromConsumerRecordException(s"$data", encoderPipeData)
  }

  val encode: PartialFunction[EncoderPipeData, Option[EventLog]] =
    UPP orElse UPP_UPDATE orElse PublicBlockchain orElse PublicKey orElse OrElse

}
