package com.ubirch.dispatcher.process

import javax.inject._

//@Singleton
//class FilterEmpty @Inject() (implicit ec: ExecutionContext)
//  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
//  with LazyLogging {
//
//  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {
//    val dispatcherData = DispatcherPipeData.empty.withConsumerRecords(v1)
//    if (v1.nonEmpty) {
//      dispatcherData
//    } else {
//      throw EmptyValueException("No Consumer Records Found", dispatcherData)
//    }
//
//  }
//
//  def apply(v1: ConsumerRecord[String, String]): Future[DispatcherPipeData] = apply(Vector(v1))
//
//}
//
///**
//  * Executor that transforms a ConsumerRecord into an EventLog
//  * @param ec Represent the execution context for asynchronous processing.
//  */
//@Singleton
//class EventLogParser @Inject() (implicit ec: ExecutionContext)
//  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
//  with LazyLogging {
//
//  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = v1.map { v1 =>
//    val result: DispatcherPipeData = try {
//      val eventLog = v1.consumerRecords.map { x =>
//        logger.debug("EventLogParser:" + x.value())
//        EventLogJsonSupport.FromString[EventLog](x.value()).get
//      }
//      v1.copy(eventLog = eventLog.map(_.addTraceHeader(Values.DISPATCHER_SYSTEM)))
//    } catch {
//      case e: Exception =>
//        logger.error("Error Parsing Event: " + e.getMessage)
//        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1)
//    }
//
//    result
//  }
//
//}
//
///**
//  * Represents an executor that creates the producer record object that will be eventually published to Kafka
//  *
//  * @param config Represents a config object to read config values from
//  * @param ec     Represents an execution context
//  */
//@Singleton
//class CreateProducerRecords @Inject() (
//    config: Config,
//    dispatchInfo: DispatchInfo,
//    @Named(DefaultDispatchingCounter.name) counter: Counter
//)(implicit ec: ExecutionContext)
//  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
//  with ProducerConfPaths
//  with LazyLogging {
//  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {
//
//    import EventLogJsonSupport._
//
//    v1.map { v1 =>
//
//      try {
//
//        val output = v1.eventLog
//          .flatMap(x => dispatchInfo.info.find(_.category == x.category).map(y => (x, y)))
//          .flatMap { case (x, y) =>
//
//            val commitDecision: Vector[Decision[ProducerRecord[String, String]]] = {
//
//              import org.json4s._
//
//              //logger.debug(s"Creating PR for EventLog(${x.category}, ${x.id})")
//
//              val eventLogJson = EventLogJsonSupport.ToJson[EventLog](x).get
//
//              y.topics.map { t =>
//
//                val dataToSend: String = t.dataToSend.filter(_.nonEmpty).flatMap { dts =>
//                  val dataFromEventLog = eventLogJson \ dts
//                  dataFromEventLog.extractOpt[String]
//                }.orElse {
//                  val data = Option(x.toJson)
//                  counter.counter.labels(t.name).inc()
//                  data
//                }.getOrElse(throw CreateProducerRecordException("Empty Materials 2: No data field extracted.", v1))
//
//                logger.debug(s"Dispatching to $t: ")
//
//                Go(ProducerRecordHelper.toRecord(t.name, x.id.toString, dataToSend, Map.empty))
//
//              }.toVector
//
//            }
//
//            commitDecision
//          }
//
//        //.getOrElse(throw CreateProducerRecordException("Empty Materials 1", v1))
//        //TODO REMOVING SETTING FOR TESTING!!
//        v1.copy(producerRecords = Vector.empty)
//
//      } catch {
//
//        case e: Exception =>
//          throw CreateProducerRecordException(s"dispatchInfo: ${dispatchInfo.info} / current error: ${e.getMessage}", v1)
//
//      }
//    }
//
//  }
//}
//
//@Singleton
//class Commit @Inject() (basicCommitter: BasicCommitUnit, metricsLoggerBasic: MetricsLoggerBasic)(implicit ec: ExecutionContext)
//  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
//  with LazyLogging {
//
//  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {
//
//    v1.map(_.producerRecords).foreach { prs =>
//      prs.foreach { x =>
//        basicCommitter(x)
//      }
//    }
//    v1
//  }
//}

//@Singleton
//class Commit @Inject() (basicCommitter: BasicCommit, metricsLoggerBasic: MetricsLoggerBasic)(implicit ec: ExecutionContext)
//  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
//  with LazyLogging {
//
//  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {
//
//    val futureMetadata = v1.map(_.producerRecords)
//      .flatMap { prs =>
//        Future.sequence {
//          prs.map { x =>
//            val futureResp = basicCommitter(x)
//            futureResp.map {
//              case Some(_) => metricsLoggerBasic.incSuccess
//              case None => metricsLoggerBasic.incFailure
//            }
//            futureResp
//          }
//        }
//      }.map { x =>
//        x.flatMap(_.toVector)
//      }
//
//    val futureResp = for {
//      md <- futureMetadata
//      v <- v1
//    } yield {
//      v.copy(recordsMetadata = md)
//    }
//
//    futureResp.recoverWith {
//      case e: Exception =>
//        logger.error("Error committing: {} ", e.getMessage)
//        v1.flatMap { x =>
//          Future.failed {
//            CommitException(e.getMessage, x)
//          }
//        }
//    }
//
//  }
//}

/**
  * Represents a description of a family of executors that can be composed.
  */
trait ExecutorFamily {

  //  def filterEmpty: FilterEmpty
  //  def eventLogParser: EventLogParser
  //  def createProducerRecords: CreateProducerRecords
  //  def commit: Commit
  def dispatch: Dispatch

}

@Singleton
class DefaultExecutorFamily @Inject() (
    //    val filterEmpty: FilterEmpty,
    //    val eventLogParser: EventLogParser,
    //    val createProducerRecords: CreateProducerRecords,
    //    val commit: Commit,
    val dispatch: Dispatch
) extends ExecutorFamily
