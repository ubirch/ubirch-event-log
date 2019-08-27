package com.ubirch.discovery.process
//
//
//@Singleton
//class DiscoveryExecutor @Inject() (basicCommit: BasicCommit, config: Config)(implicit ec: ExecutionContext)
//  extends Executor[Future[PipeData], Future[PipeData]]
//  with ProducerConfPaths
//  with LazyLogging {
//
//  val topic: String = config.getString(TOPIC_PATH)
//
//  logger.debug("Discovery Topic: " + topic)
//
//  override def apply(v1: Future[PipeData]): Future[PipeData] = {
//    v1.flatMap { v1 =>
//
//      v1.discoveryData match {
//        case Nil =>
//          Future.successful(v1)
//        case _ =>
//
//          val relationsAsString = EventLogJsonSupport.ToJson(v1.discoveryData).toString
//
//          val pr = ProducerRecordHelper.toRecord(topic, null, relationsAsString, Map.empty)
//
//          basicCommit(Go(pr)).map {
//            case Some(_) =>
//              logger.debug("Discovery data successfully sent")
//              v1
//            case None =>
//              logger.error(s"Error sending discovery data to topic $topic")
//              throw DiscoveryException("Error sending discovery data", v1, "Error Committing Discovery Data")
//          }
//      }
//
//    }
//
//  }
//
//}
