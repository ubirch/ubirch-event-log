package com.ubirch.dispatcher.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.services.metrics.Counter
import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.util._
import javax.inject._
import monix.eval.Task
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

@Singleton
class DispatchExecutor @Inject() (
    @Named(DefaultDispatchingCounter.name) counter: Counter,
    dispatchInfo: DispatchInfo,
    config: Config,
    lifecycle: Lifecycle,
    stringProducer: StringProducer
)(implicit val ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
  with LazyLogging {

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

  def run(consumerRecord: ConsumerRecord[String, String]) = Task.defer {
    val pipeData = DispatcherPipeData.empty.withConsumerRecords(Vector(consumerRecord))
    val (eventLog, eventLogJson) = Try {
      val fsEventLog = EventLogJsonSupport.FromString[EventLog](consumerRecord.value())
      val el = fsEventLog.get
      val elj = fsEventLog.json
      (el, elj)
    }.getOrElse(throw ParsingIntoEventLogException("Error Parsing Event Log", pipeData))

    val prs = Try(createProducerRecords(eventLog, eventLogJson))
      .getOrElse(throw CreateProducerRecordException("Error Creating Producer Records", pipeData.withEventLogs(Vector(eventLog))))

    Task.gather(prs.map(x => Task.fromFuture(stringProducer.send(x))))
  }

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {

    try {

      val pipeData = DispatcherPipeData.empty.withConsumerRecords(v1)

      if (v1.headOption.isEmpty) {
        throw EmptyValueException("No Records Found to be processed", pipeData)
      }

      v1.foreach(run)

      DispatcherPipeData.empty.withConsumerRecords(v1)

    } catch {
      case e: ExecutionException =>
        throw e
      case e: KafkaException =>
        logger.error("Error: " + e.getMessage)
        DispatcherPipeData.empty.withConsumerRecords(v1)
    }

  }

}

trait ExecutorFamily {
  def dispatch: DispatchExecutor
}

@Singleton
class DefaultExecutorFamily @Inject() (val dispatch: DispatchExecutor) extends ExecutorFamily
