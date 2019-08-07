package com.ubirch.encoder.process

import java.io.ByteArrayInputStream

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.encoder.models.Encodings
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.services.metrics.DefaultEncodingsCounter
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.process.Executor
import com.ubirch.services.metrics.Counter
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EncoderExecutor @Inject() (
    @Named(DefaultEncodingsCounter.name) counter: Counter,
    encodings: Encodings,
    config: Config,
    stringProducer: StringProducer
)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, Array[Byte]]], Future[EncoderPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._

  val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

  override def apply(v1: Vector[ConsumerRecord[String, Array[Byte]]]): Future[EncoderPipeData] = Future {

    val jValuesBuff = scala.collection.mutable.ListBuffer.empty[JValue]

     val res = v1.map { x =>

      Future {
        try {

          val bytes = new ByteArrayInputStream(x.value())
          val jValue = parse(bytes)

          jValuesBuff += jValue

          val ldp = EncoderPipeData(Vector(x), Vector(jValue))

          val maybePR = encodings.UPP(ldp).orElse(encodings.PublichBlockchain(ldp)).orElse(encodings.OrElse(ldp))(jValue).map { el =>
            val elSigned = el.addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM).sign(config)
            EncoderJsonSupport.ToJson[EventLog](elSigned)
            ProducerRecordHelper.toRecord(topic, elSigned.id, elSigned.toJson, Map.empty)
          }

          val sent = maybePR.map { x =>
            stringProducer.getProducerOrCreate.send(x)
          }

          sent.getOrElse(throw EncodingException("Error in the Encoding Process: No PR to send", EncoderPipeData(Vector(x), jValuesBuff.toVector)))

        } catch {
          case e: Exception =>
            throw EncodingException("Error in the Encoding Process: " + e.getMessage, EncoderPipeData(Vector(x), jValuesBuff.toVector))
        }

      }

    }

    Future.sequence(res).map{ _ =>
      EncoderPipeData(v1, jValuesBuff.toVector)
    }



  }.flatten

}

trait ExecutorFamily {
  val encoderExecutor: EncoderExecutor
}

@Singleton
class DefaultExecutorFamily @Inject() (val encoderExecutor: EncoderExecutor) extends ExecutorFamily
