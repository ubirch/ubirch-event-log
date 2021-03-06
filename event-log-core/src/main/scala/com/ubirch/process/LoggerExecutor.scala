package com.ubirch.process

import com.datastax.driver.core.exceptions.{ InvalidQueryException, NoHostAvailableException }
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, StoreConfPaths }
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, EventsDAO, Values }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Exceptions.{ ParsingIntoEventLogException, EventLogDatabaseException }
import javax.inject._
import monix.eval.Task
import monix.execution.Scheduler
import org.apache.kafka.clients.consumer.ConsumerRecord

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

  lazy val storeLookups: Boolean = config.getBoolean(StoreConfPaths.STORE_LOOKUPS)

  logger.info("storing_lookups={}", storeLookups)

  val scheduler: Scheduler = monix.execution.Scheduler(ec)

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = {
    val promise = Promise[PipeData]()
    Task
      .gather(v1.map(x => run(x)))
      .runOnComplete {
        case Success(_) => promise.success(PipeData(v1, None))
        case Failure(exception) => promise.failure(exception)
      }(scheduler)
    promise.future
  }

  def run(consumerRecord: ConsumerRecord[String, String]): Task[Int] = {

    Task.defer {

      for {
        el <- Task.delay(Try(
          EventLogJsonSupport.FromString[EventLog](consumerRecord.value()).get
            .addTraceHeader(Values.EVENT_LOG_SYSTEM)
        ).getOrElse(throw ParsingIntoEventLogException("Error Parsing Into Event Log", PipeData(consumerRecord, None))))

        res <- Task
          .fromFuture(processEventLog(el))
          .doOnFinish {
            case Some(_) => Task.delay(failureCounter.counter.labels(metricsSubNamespace).inc())
            case None => Task.delay(successCounter.counter.labels(metricsSubNamespace).inc())
          }.onErrorRecover {
            case e: NoHostAvailableException =>
              logger.error("Error connecting to host: " + e)
              throw e
            case e: InvalidQueryException =>
              logger.error("Error processing data (invalid query): " + e)
              throw e
            case e: Exception =>
              logger.error("Error processing data (other): " + e)
              throw EventLogDatabaseException("Error processing data (other)", PipeData(consumerRecord, Some(el)), e.getMessage)
          }

      } yield {
        res
      }

    }

  }

  private def processEventLog(eventLog: EventLog): Future[Int] = {
    eventLog.category match {
      case Values.UPP_ENABLE_CATEGORY =>
        logger.warn(s"it has not been implemented yet. category: ${eventLog.category}")
        Future.successful(0)
      case Values.UPP_DISABLE_CATEGORY =>
        logger.warn(s"it has not been implemented yet. category: ${eventLog.category}")
        Future.successful(0)
      case Values.UPP_DELETE_CATEGORY =>
        logger.info(s"delete event log. id: ${eventLog.id}")
        // the target UPP that is deleted should have an UPP category
        events.deleteFromEventLog(eventLog.copy(category = Values.UPP_CATEGORY))
      case _ =>
        logger.info(s"store event log. id: ${eventLog.id}, category: ${eventLog.category}")
        if (storeLookups) events.insertFromEventLog(eventLog)
        else events.insertFromEventLogWithoutLookups(eventLog)
    }
  }
}

