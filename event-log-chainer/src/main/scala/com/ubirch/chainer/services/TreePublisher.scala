//package com.ubirch.chainer.services
//
//import com.typesafe.config.Config
//import com.typesafe.scalalogging.LazyLogging
//import com.ubirch.ConfPaths.ConsumerConfPaths
//import com.ubirch.kafka.producer.StringProducer
//import com.ubirch.util.Implicits.enrichedConfig
//import com.ubirch.services.metrics.{Counter, DefaultMetricsLoggerCounter}
//import com.ubirch.util.ProducerRecordHelper
//import javax.inject._
//
//import scala.concurrent.ExecutionContext
//
//@Singleton
//class TreePublisher @Inject() (stringProducer: StringProducer,
//                        @Named(DefaultMetricsLoggerCounter.name) counter: Counter,
//                        config: Config)(implicit ec: ExecutionContext) extends LazyLogging {
//
//  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)
//
//  def createProducerRecord(topic: String) = {
//    val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")
//  }
//
//  def publish() = {
//
//    val futureMetadata = v1.map(_.producerRecords)
//      .flatMap { prs =>
//        Future.sequence {
//          prs.map { x =>
//            val futureResp = stringProducer.send(x)
//            futureResp.onComplete {
//              case Success(_) =>
//                counter.counter.labels(metricsSubNamespace, Values.SUCCESS).inc()
//              case Failure(exception) =>
//                logger.error("Error publishing ", exception)
//                counter.counter.labels(metricsSubNamespace, Values.FAILURE).inc()
//            }
//            futureResp
//          }
//        }
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
//        v1.flatMap { x =>
//          Future.failed {
//            CommitException(e.getMessage, x)
//          }
//        }
//    }
//
//  }
//}
