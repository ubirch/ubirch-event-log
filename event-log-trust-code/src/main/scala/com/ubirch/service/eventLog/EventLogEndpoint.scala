package com.ubirch.service.eventLog

import java.net.URL
import java.util.concurrent.Executor

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLog
import com.ubirch.service.httpClient.WebClient
import com.ubirch.util.{ EventLogJsonSupport, URLsHelper }
import javax.inject.Inject
import org.asynchttpclient.Param

import scala.concurrent.ExecutionContext

class EventLogEndpoint @Inject() (webClient: WebClient, config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  val logQueryEndpointAsString = config.getString("eventLog.logEndpoint.events")
  val logQueryEndpointAsURL = URLsHelper.toURL(logQueryEndpointAsString).get
  val simpleLogQueryEndpointAsString = config.getString("eventLog.logEndpoint.events2")
  val simpleLogQueryEndpointAsUrl = URLsHelper.toURL(simpleLogQueryEndpointAsString).get

  require(logQueryEndpointAsString.nonEmpty, "Log Query Endpoint not found. Please check \"eventLog.logEndpoint.events\" ")
  require(simpleLogQueryEndpointAsString.nonEmpty, "Log Query Endpoint not found. Please check \"eventLog.logEndpoint.events2\" ")

  def query(url: URL, queryParams: List[Param]) = {

    val executor = ec.asInstanceOf[Executor with ExecutionContext]
    val queryParamsAsString = queryParams.map(x => x.getName + "=" + x.getValue).mkString(",")

    logger.info("params={}", queryParamsAsString)

    webClient
      .get(url.toString)(queryParams)(executor)
      .map { res =>

        logger.info("endpoint={} status_code_text={} status_code={} content_type={}", url.toString, res.getStatusText, res.getStatusCode, res.getContentType)

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
            throw new Exception("Invalid parameters calling " + url.toString)
          } //Invalid Params
          else throw new Exception("Wrong response from " + url.toString)
        } else throw new Exception("Wrong content type. I am expecting application/json;charset=utf-8")

      }

  }

  def queryByIdAndCat(id: String, category: String) = {

    val queryParams = List(
      new Param("category", category),
      new Param("id", id)
    )

    query(simpleLogQueryEndpointAsUrl, queryParams)

  }

  def queryByTimeAndCat(category: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) = {

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

    query(logQueryEndpointAsURL, queryParams)

  }

}
