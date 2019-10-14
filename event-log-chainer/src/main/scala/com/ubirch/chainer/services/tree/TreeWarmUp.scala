package com.ubirch.chainer.services.tree

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.httpClient.WebClient
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.util.{ EventLogJsonSupport, TimeHelper, URLsHelper }
import javax.inject._
import org.json4s.JsonDSL._

import scala.concurrent.{ ExecutionContext, Future }

sealed trait WarmUpResult

case object AllGood extends WarmUpResult
case object WhatTheHeck extends WarmUpResult
case object CreateGenesisTree extends WarmUpResult

@Singleton
class TreeWarmUp @Inject() (treeCache: TreeCache, webClient: WebClient, config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  val logQueryEndpointAsString = config.getString("eventLog.logQueryEndpoint")

  val logQueryEndpointAsURL = URLsHelper.toURL(logQueryEndpointAsString).get

  require(logQueryEndpointAsString.nonEmpty, "Log Query Endpoint not found. Please check \"eventLog.logQueryEndpoint\" ")

  def warmup: Future[WarmUpResult] = {

    logger.info("Starting Tree Warm-up...")

    for {
      mfe <- firstEver
      mlt <- lastest
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

  def firstEver: Future[Option[String]] = {

    logger.info("Checking Genesis Tree ...")

    val executor = ec.asInstanceOf[Executor with ExecutionContext]
    val bigBangTime = TimeHelper.bigBangAsDate
    val body = {

      val _body =
        ("category" -> Values.MASTER_TREE_CATEGORY) ~
          ("year" -> bigBangTime.getYear) ~
          ("month" -> bigBangTime.getMonthOfYear) ~
          ("day" -> bigBangTime.getDayOfMonth)

      EventLogJsonSupport.stringify(_body)
    }

    logger.info("params={}", body)

    webClient
      .post(logQueryEndpointAsURL.toString)(body.getBytes(StandardCharsets.UTF_8))(executor)
      .map { res =>

        logger.info("endpoint={} status_code_text={} status_code={} content_type={}", logQueryEndpointAsString, res.getStatusText, res.getStatusCode, res.getContentType)

        if (res.getContentType.contains("json")) {

          logger.debug("received_body={}", res.getResponseBody)

          if (res.getStatusCode == 200) { //Found

            try {
              val jvalue = EventLogJsonSupport.getJValue(res.getResponseBodyAsStream)
              val eventLogJValue = jvalue \\ "data"
              val bigBangEventLog = EventLogJsonSupport.FromJson[List[EventLog]](eventLogJValue).get

              logger.info("big_bang_event_log_found:" + bigBangEventLog)

              Some(bigBangEventLog.head.id)

            } catch {
              case e: Exception =>
                logger.error("Error parsing Big Bang Tree", e)
                throw new Exception("Error parsing into event log.")
            }

          } else if (res.getStatusCode == 404) { // Not found
            logger.info("No Big Bang Tree found")
            None
          } else if (res.getStatusCode == 400) {
            logger.error("invalid_parameters={}" + body)
            throw new Exception("Invalid parameters calling " + logQueryEndpointAsString)
          } //Invalid Params
          else throw new Exception("Wrong response from " + logQueryEndpointAsString)
        } else throw new Exception("Wrong content type. I am expecting application/json;charset=utf-8")

      }

  }

  def lastest: Future[Option[String]] = {
    logger.info("Checking Latest Tree ...")
    Future.successful(None)
  }

}
