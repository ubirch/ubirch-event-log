package com.ubirch.process

import com.datastax.driver.core.exceptions.{ InvalidQueryException, NoHostAvailableException }
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, EventsDAO, Values }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Exceptions.{ ParsingIntoEventLogException, StoringIntoEventLogException }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

@Singleton
class LoggerExecutor @Inject() (
    events: EventsDAO,
    @Named(DefaultSuccessCounter.name) successCounter: Counter,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config
)(implicit val ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] with LazyLogging {

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  import monix.eval._

  val circuitBreaker = TaskCircuitBreaker(
    maxFailures = 5,
    resetTimeout = 10.seconds
  ).doOnOpen(Task(logger.info("Circuit breaker is open")))
    .doOnClosed(Task(logger.warn("Circuit breaker is closed")))

  val scheduler = monix.execution.Scheduler(ec)

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {
    val promise = Promise[PipeData]()
    Task.gather(v1.map(x => circuitBreaker.protect(run(x)))).runOnComplete {
      case Success(_) =>
        promise.success(PipeData(v1, None))
      case Failure(exception) => promise.failure(exception)
    }(scheduler)
    promise.future
  }

  def run(consumerRecord: ConsumerRecord[String, String]) = {

    Task.defer {

      val el = Try(EventLogJsonSupport.FromString[EventLog](consumerRecord.value()).get.addTraceHeader(Values.EVENT_LOG_SYSTEM))
        .getOrElse(throw ParsingIntoEventLogException("Error Parsing Into Event Log", PipeData(consumerRecord, None)))

      Task.fromFuture {
        events.insertFromEventLog(el)
          .map { x =>
            successCounter.counter.labels(metricsSubNamespace).inc()
            x
          }
          .recover {
            case e: NoHostAvailableException =>
              failureCounter.counter.labels(metricsSubNamespace).inc()
              logger.error("Error connecting to host: " + e)
              throw e
            case e: InvalidQueryException =>
              failureCounter.counter.labels(metricsSubNamespace).inc()
              logger.error("Error storing data (invalid query): " + e)
              throw e
            case e: Exception =>
              failureCounter.counter.labels(metricsSubNamespace).inc()
              logger.error("Error storing data (other): " + e)
              throw StoringIntoEventLogException("Error storing data (other)", PipeData(consumerRecord, Some(el)), e.getMessage)
          }

      }
    }

  }

}

