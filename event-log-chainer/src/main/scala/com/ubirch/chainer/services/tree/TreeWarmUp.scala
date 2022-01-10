package com.ubirch.chainer.services.tree

import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.httpClient.WebClient
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.util.{ EventLogJsonSupport, TimeHelper, URLsHelper }
import javax.inject._

import org.asynchttpclient.Param
import org.joda.time.DateTime

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a result for the warmup process
  */
sealed trait WarmUpResult

/**
  * Represents the state when all is good
  */
case object AllGood extends WarmUpResult

/**
  * Represents the state when a weird situation happened
  */
case object WhatTheHeck extends WarmUpResult

/**
  * Represents the state when the Genesis Tree has been created.
  */
case object CreateGenesisTree extends WarmUpResult

/**
  * Represents a component for warming up the process with previous trees.
  * @param treeCache Represents a components for caching trees
  * @param webClient Represents a simple web client to talk to the event log to get the genesis event log tree
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
@Singleton
class TreeWarmUp @Inject() (treeCache: TreeCache, webClient: WebClient, config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  val logQueryEndpointAsString: String = config.getString(TreePaths.LOG_QUERY_ENDPOINT)
  val logQueryEndpointAsURL: URL = URLsHelper.toURL(logQueryEndpointAsString).get
  val daysBack: Int = config.getInt(TreePaths.DAYS_BACK)

  require(logQueryEndpointAsString.nonEmpty, "Log Query Endpoint not found. Please check \"eventLog.logQueryEndpoint\" ")
  require(daysBack > 1, "Days back has to be lt 1")

  def warmup: Future[WarmUpResult] = {

    logger.info("Starting Tree Warm-up...")

    for {
      mfe <- firstEver
      mlt <- latest
    } yield {
      (mfe.filter(_.nonEmpty), mlt.filter(_.nonEmpty)) match {
        case (Some(fe), None) =>
          logger.info("Genesis Tree and No Latest Tree found. Setting local as Genesis as [{}]", fe)
          treeCache.setLatestHash(fe)
          AllGood
        case (Some(_), Some(lt)) =>
          logger.info("Genesis Tree and Latest Tree found. Setting local Latest as [{}]", lt)
          treeCache.setLatestHash(lt)
          AllGood
        case (None, Some(_)) =>
          logger.error("WHAT: There's a latest tree BUT no genesis! ")
          WhatTheHeck
        case (None, None) =>
          logger.info("Nothing found. This is the beginning of the universe. Creating first Tree Ever")
          CreateGenesisTree
      }
    }

  }

  private def query(category: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) = {

    logger.info("Querying for Trees ...")

    val executor = ec.asInstanceOf[Executor with ExecutionContext]

    val queryParams = List(
      new Param("category", category),
      new Param("year", year.toString),
      new Param("month", month.toString),
      new Param("day", day.toString),
      new Param("hour", hour.toString),
      new Param("minute", minute.toString),
      new Param("second", second.toString),
      new Param("milli", milli.toString)
    )

    val queryParamsAsString = queryParams.map(x => x.getName + "=" + x.getValue).mkString(",")

    logger.info("params={}", queryParamsAsString)

    webClient
      .get(logQueryEndpointAsURL.toString)(queryParams)(executor)
      .map { res =>

        logger.info("endpoint={} status_code_text={} status_code={} content_type={}", logQueryEndpointAsString, res.getStatusText, res.getStatusCode, res.getContentType)

        if (res.getContentType.contains("json")) {

          logger.debug("received_body={}", res.getResponseBody)

          if (res.getStatusCode == 200) { //Found

            try {
              val jvalue = EventLogJsonSupport.getJValue(res.getResponseBodyAsStream)
              val eventLogJValue = jvalue \\ "data"
              val eventLogs = EventLogJsonSupport.FromJson[List[EventLog]](eventLogJValue).get

              eventLogs

            } catch {
              case e: Exception =>
                logger.error("Error parsing into event logs", e)
                throw new Exception("Error parsing into event log.")
            }

          } else if (res.getStatusCode == 404) { // Not found
            logger.info("No Tree found with these params {}", queryParamsAsString)
            Nil
          } else if (res.getStatusCode == 400) {
            logger.error("invalid_parameters={}", queryParamsAsString)
            throw new Exception("Invalid parameters calling " + logQueryEndpointAsString)
          } //Invalid Params
          else throw new Exception("Wrong response from " + logQueryEndpointAsString)
        } else throw new Exception("Wrong content type. I am expecting application/json;charset=utf-8")

      }

  }

  def firstEver: Future[Option[String]] = {

    logger.info("Checking Genesis Tree ...")

    val bgt = TimeHelper.bigBangAsDate

    query(
      Values.MASTER_TREE_CATEGORY,
      bgt.getYear,
      bgt.getMonthOfYear,
      bgt.getDayOfMonth,
      bgt.getHourOfDay,
      bgt.getMinuteOfHour,
      bgt.getSecondOfMinute,
      bgt.getMillisOfSecond
    )
      .map(_.headOption)
      .map { bbt =>
        logger.info("big_bang_event_log_found:" + bbt)
        bbt.map(_.id)
      }

  }

  def latest: Future[Option[String]] = {

    logger.info("Checking Latest Tree ...")

    val now = TimeHelper.now
    val daysBackCounter = new AtomicInteger(daysBack)

    def check(dateTime: DateTime): Future[Option[String]] = {
      query(
        Values.MASTER_TREE_CATEGORY,
        dateTime.getYear,
        dateTime.getMonthOfYear,
        dateTime.getDayOfMonth,
        -1,
        -1,
        -1,
        -1
      )
        .map(_.filter(x => new DateTime(x.eventTime) != TimeHelper.bigBangAsDate))
        .map(_.headOption)
        .flatMap {
          case v @ Some(el) =>
            logger.info("lastest_tree_event_log_found:" + el)
            Future.successful(v.map(_.id))
          case None =>
            if (daysBackCounter.getAndDecrement() == 0) {
              logger.warn("Went back {} and didn't find any tree", daysBack)
              Future.successful(None)
            } else check(dateTime.minusDays(1))
        }
    }

    check(now)

  }

}
