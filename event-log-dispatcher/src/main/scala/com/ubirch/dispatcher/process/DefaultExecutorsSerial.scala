package com.ubirch.dispatcher.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.kafka.producer.Configs
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.models.EventLog
import com.ubirch.process.{ BasicCommitUnit, Executor }
import com.ubirch.services.kafka.producer.DefaultStringProducer
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.services.metrics.Counter
import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.apache.kafka.common.KafkaException
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

@Singleton
class Dispatch @Inject() (
    @Named(DefaultDispatchingCounter.name) counter: Counter,
    dispatchInfo: DispatchInfo,
    config: Config,
    lifecycle: Lifecycle
)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
  with LazyLogging {

  def parse(consumerRecord: ConsumerRecord[String, String]) = {
    EventLogJsonSupport.FromString[EventLog](consumerRecord.value())
  }

  def createProducerRecords(eventLog: EventLog, eventLogJson: JValue): Vector[ProducerRecord[String, String]] = {

    import EventLogJsonSupport._

    dispatchInfo.info.find(_.category == eventLog.category)
      .map { y =>

        val commitDecisions = {

          import org.json4s._

          y.topics.map { t =>

            val dataToSend: String = t.dataToSend.filter(_.nonEmpty).flatMap { dts =>
              val dataFromEventLog = eventLogJson \ dts
              dataFromEventLog.extractOpt[String]
            }.orElse {
              val data = Option(eventLog.toJson)
              counter.counter.labels(t.name).inc()
              data
            }.getOrElse(throw DispatcherProducerRecordException("Empty Materials 2: No data field extracted.", eventLog.toJson))

            ProducerRecordHelper.toRecord(t.name, eventLog.id, dataToSend, Map.empty)

          }.toVector

        }

        commitDecisions
      }.getOrElse(throw DispatcherProducerRecordException("Empty Materials 1: No Dispatching Info", eventLog.toJson))

  }

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {

    val stringProducer = new DefaultStringProducer(config, lifecycle) {
      override def configs: ConfigProperties = Configs(
        bootstrapServers = bootstrapServers
      //,enableIdempotence = true
      //,transactionalIdConfig = Some("event-log-dispatcher-basic-publish")
      )
    }.get()

    //stringProducer.getProducerOrCreate.initTransactions()

    try {

      // stringProducer.getProducerOrCreate.beginTransaction()
      //logger.debug("Starting transaction")

      val pipeData = DispatcherPipeData.empty.withConsumerRecords(v1)

      if (v1.headOption.isEmpty) {
        throw EmptyValueException("No Records Found to be processed", pipeData)
      }

      v1.foreach { cr =>

        val (eventLog, eventLogJson) = Try {
          val fsEventLog = parse(cr)
          val el = fsEventLog.get
          val elj = fsEventLog.json
          (el, elj)
        }.getOrElse(throw ParsingIntoEventLogException("Error Parsing Event Log", pipeData))

        val prs = Try(createProducerRecords(eventLog, eventLogJson))
          .getOrElse(throw CreateProducerRecordException("Error Creating Producer Records", pipeData.withEventLogs(Vector(eventLog))))

        prs.map(x => stringProducer.getProducerOrCreate.send(x))

      }

      //stringProducer.getProducerOrCreate.commitTransaction()
      stringProducer.getProducerOrCreate.close()
      //logger.debug("Committing transaction")
      DispatcherPipeData.empty.withConsumerRecords(v1)

    } catch {
      case e: ExecutionException =>
        throw e
      case e: KafkaException =>
        //stringProducer.getProducerOrCreate.abortTransaction()
        logger.error("Error: " + e.getMessage)
        DispatcherPipeData.empty.withConsumerRecords(v1)
    }

  }

}
