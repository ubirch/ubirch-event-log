package com.ubirch.lookup.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLogRow, EventsDAO, Values }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
class CassandraFinder @Inject() (eventsDAO: EventsDAO)(implicit ec: ExecutionContext) extends LazyLogging {

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]] = {
    queryType match {
      case Payload => eventsDAO.events.byIdAndCat(value, Values.UPP_CATEGORY).map(_.headOption)
      case Signature => eventsDAO.eventLogRowByLookupRowInfo(value, Signature.value, Values.UPP_CATEGORY)
    }
  }

  def findSlaveTreeThruLookup(uppEventLog: EventLogRow): Future[Option[EventLogRow]] = {
    eventsDAO.eventLogRowByLookupRowInfo(uppEventLog.id, Values.SLAVE_TREE_ID, Values.SLAVE_TREE_CATEGORY)
  }

  def findMasterTreeThruLooup(uppEventLog: EventLogRow): Future[Option[EventLogRow]] = {
    eventsDAO.eventLogRowByLookupRowInfo(uppEventLog.id, Values.MASTER_TREE_ID, Values.MASTER_TREE_CATEGORY)
  }

  def findAnchorsThruLookup(treeEventLog: EventLogRow): Future[Seq[EventLogRow]] = {
    eventsDAO.eventLogRowByLookupValueAndCategory(treeEventLog.id, Values.PUBLIC_CHAIN_CATEGORY)
  }

  def findAll(value: String, queryType: QueryType): Future[(Option[EventLogRow], Option[EventLogRow], Seq[EventLogRow])] = {
    val fres = findUPP(value, queryType).flatMap {
      case upp @ Some(uppEl) =>
        findSlaveTreeThruLookup(uppEl).flatMap {
          case Some(slaveTreeEl) =>
            findMasterTreeThruLooup(slaveTreeEl).flatMap {
              case tree @ Some(masterTreeEl) =>
                findAnchorsThruLookup(masterTreeEl).map(ax => (upp, tree, ax))
              case None => Future.successful((upp, None, Seq.empty))
            }
          case None => Future.successful((upp, None, Seq.empty))
        }
      case None => Future.successful((None, None, Seq.empty))
    }

    fres.onComplete {
      case Success(res) =>
        logger.debug("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, res.toString())

      case Failure(exception) =>
        logger.error("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, exception.getMessage)

    }

    fres
  }

}
