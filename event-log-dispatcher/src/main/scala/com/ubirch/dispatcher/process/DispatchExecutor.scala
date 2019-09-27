package com.ubirch.dispatcher.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.{ EventLog, EventLogSerializer, HeaderNames, Values }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.services.metrics.{ Counter, DefaultMetricsLoggerCounter }
import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.util._
import javax.inject._
import monix.eval.Task
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class DispatchExecutor @Inject() (
    reporter: Reporter,
    @Named(DefaultDispatchingCounter.name) counterPerTopic: Counter,
    @Named(DefaultMetricsLoggerCounter.name) results: Counter,
    dispatchInfo: DispatchInfo,
    config: Config,
    lifecycle: Lifecycle,
    stringProducer: StringProducer
)(implicit val ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
  with LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(ec)

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  val dispatchingInfo = dispatchInfo.info

  def createProducerRecords(eventLog: EventLog, eventLogJValue: JValue, eventLogAsString: String): Vector[ProducerRecord[String, String]] = {

    import EventLogJsonSupport._
    import org.json4s._

    val prs =
      dispatchingInfo
        .find(_.category == eventLog.category)
        .map { dispatch =>

          dispatch.topics.toVector.flatMap { dispatchTopic =>

            lazy val tagsToExclude = ((eventLogJValue \ EventLogSerializer.HEADERS) \ HeaderNames.DISPATCHER)
              .extractOpt[List[String]]
              .getOrElse(Nil)
              .flatMap(_.split("tags-exclude:"))
              .filter(_.nonEmpty)
              .distinct

            lazy val dataToSend: String = dispatchTopic.dataToSend
              .filter(_.nonEmpty)
              .flatMap(dts => (eventLogJValue \ dts).extractOpt[String])
              .orElse(Option(eventLogAsString))
              .map { x =>
                counterPerTopic.counter.labels(metricsSubNamespace, dispatchTopic.name).inc()
                x
              }
              .getOrElse(throw DispatcherProducerRecordException("Empty Materials 2: No data field extracted.", eventLog.toJson))

            if (dispatchTopic.tags.exists(tagsToExclude.contains)) Vector.empty
            else Vector(ProducerRecordHelper.toRecord(dispatchTopic.name, eventLog.id, dataToSend, Map.empty))

          }

        }.getOrElse(throw DispatcherProducerRecordException("Empty Materials 1: No Dispatching Info", eventLog.toJson))

    prs

  }

  def run(consumerRecord: ConsumerRecord[String, String]) = Task.defer {
    val pipeData = DispatcherPipeData.empty.withConsumerRecords(Vector(consumerRecord))
    val (eventLog, eventLogJValue, eventLogAsString) = Try {
      val fsEventLog = EventLogJsonSupport.FromString[EventLog](consumerRecord.value())
      val el = fsEventLog.get
      val elj = fsEventLog.json
      (el, elj, consumerRecord.value())
    }.getOrElse(throw ParsingIntoEventLogException("Error Parsing Event Log", pipeData))

    val prs = Try(createProducerRecords(eventLog, eventLogJValue, eventLogAsString))
      .getOrElse(throw CreateProducerRecordException("Error Creating Producer Records", pipeData.withEventLogs(Vector(eventLog))))

    Task.gather(prs.map(x => Task.fromFuture(stringProducer.send(x))))
  }

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {

    try {

      val pipeData = DispatcherPipeData.empty.withConsumerRecords(v1)

      if (v1.headOption.isEmpty) {
        throw EmptyValueException("No Records Found to be processed", pipeData)
      }

      v1.foreach(x => run(x).runOnComplete {
        case Success(_) =>
          results.counter.labels(metricsSubNamespace, Values.SUCCESS).inc()
        case Failure(e: ParsingIntoEventLogException) =>
          logger.error("ParsingIntoEventLogException: " + e.getMessage)
          results.counter.labels(metricsSubNamespace, Values.FAILURE).inc()
        case Failure(e: CreateProducerRecordException) =>
          logger.error("CreateProducerRecordException: " + e.getMessage)
          results.counter.labels(metricsSubNamespace, Values.FAILURE).inc()
        case Failure(e) =>
          logger.error(s"${e.getClass.getName}: " + e.getMessage)
          results.counter.labels(metricsSubNamespace, Values.FAILURE).inc()
      })

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
