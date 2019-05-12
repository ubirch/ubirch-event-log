package com.ubirch.lookup.models

import com.ubirch.lookup.ServiceTraits
import com.ubirch.models.{ EventLogRow, EventsDAO, LookupKey }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class Finder @Inject() (eventsDAO: EventsDAO)(implicit ec: ExecutionContext) {

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]] = {
    queryType match {
      case Payload => eventsDAO.events.byIdAndCat(value, ServiceTraits.ADAPTER_CATEGORY).map(_.headOption)
      case Signature => eventsDAO.eventLogRowByLookupRowInfo(value, Signature.value, ServiceTraits.ADAPTER_CATEGORY)
    }
  }

  def findTree(uppEventLog: EventLogRow): Future[Option[EventLogRow]] = {
    eventsDAO.eventLogRowByLookupRowInfo(uppEventLog.id, LookupKey.SLAVE_TREE_ID, LookupKey.SLAVE_TREE)
  }

  def findAnchors(treeEventLog: EventLogRow): Future[Seq[EventLogRow]] = {
    eventsDAO.eventLogRowByLookupValueAndCategory(treeEventLog.id, "PUBLIC_CHAIN")
  }

  def findAll(value: String, queryType: QueryType): Future[(Option[EventLogRow], Option[EventLogRow], Seq[EventLogRow])] = {
    findUPP(value, queryType).flatMap {
      case upp @ Some(uppEl) =>
        findTree(uppEl).flatMap {
          case tree @ Some(treeEl) => findAnchors(treeEl).map(ax => (upp, tree, ax))
          case None => Future.successful((upp, None, Seq.empty))
        }
      case None => Future.successful((None, None, Seq.empty))
    }
  }

}
