package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models._
import com.ubirch.util.EventLogJsonSupport
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

import scala.concurrent.{ ExecutionContext, Promise }
import scala.util.{ Failure, Success }

class EventLogController @Inject() (val swagger: Swagger, eventsByCat: EventsByCat, eventsDAO: EventsDAO)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging
  with FutureSupport {

  override protected def applicationDescription: String = "EventLog Getter"
  override protected implicit def jsonFormats: Formats = EventLogJsonSupport.formats

  before() {
    contentType = formats("json")
  }

  val infoSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[InfoGenericResponse]("Basic API Info")
      summary "Shows the description of the API"
      description "It serves the basic purpose of providing basic information about the API"
      tags "Description of the API"
      parameters ())

  get("/", operation(infoSwagger)) {
    InfoGenericResponse(success = true, "Successfully Processed",
      Info(RestApiInfo.title, RestApiInfo.description, swagger.apiVersion))
  }

  val events2Swagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[EventLogGenericResponse]("Returns EventLog based on category and time elements.")
      summary "Queries for event logs that have a common category and time elements."
      description "Queries for event logs that have a common category and time elements."
      tags "Queries for event logs"
      parameters (
        queryParam[String]("category").description("The category of the event-log you are interested in looking up."),
        queryParam[String]("id").description("The id of the event log")
      ))

  get("/events2", operation(events2Swagger)) {

    val (category, id) = try {
      (params("category"), params("id"))
    } catch {
      case e: Exception =>
        logger.error("invalid_params_2={}, error={}", request.getQueryString, e.getMessage)
        halt(BadRequest(EventLogGenericResponse(success = false, "Error reading query params", Nil)))
    }

    logger.debug("params={}", request.getQueryString)

    val res = eventsDAO.events.byIdAndCat(id, category)

    val promise = Promise[ActionResult]()

    res.onComplete {

      case Success(value) =>
        logger.info("events_query_result=:  " + value)
        val response = {
          EventLogGenericResponse(
            success = true,
            "Request successfully processed",
            value.map(x => EventLogRow.toEventLog(x))
          )
        }
        val r = if (value.isEmpty) NotFound(response) else Ok(response)

        promise.success(r)
      case Failure(exception) =>
        logger.error("Error querying:" + exception.getMessage)
        val r = InternalServerError(
          EventLogGenericResponse(
            success = false,
            "Something happened when processing this request.",
            Nil
          )
        )
        promise.success(r)
    }

    val finalRes = new AsyncResult() {
      override val is = promise.future
    }
    finalRes

  }

  val eventsSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[EventLogGenericResponse]("Returns EventLog based on category and time elements.")
      summary "Queries for event logs that have a common category and time elements."
      description "Queries for event logs that have a common category and time elements."
      tags "Queries for event logs"
      parameters (
        queryParam[String]("category").description("The category of the event-log you are interested in looking up."),
        queryParam[Int]("id").description("The id of the event log"),
        queryParam[Int]("month").description("The month of the event"),
        queryParam[Int]("day").description("The day of the event"),
        queryParam[Int]("hour").description("The hour of the event").optional,
        queryParam[Int]("minute").description("The minutes of the event").optional,
        queryParam[Int]("second").description("The seconds of the event").optional,
        queryParam[Int]("milli").description("The millis of the event").optional
      ))

  get("/events", operation(eventsSwagger)) {

    val query = try {

      Option(
        QueryByCatAndTimeElems(
          params("category"),
          params("year").toInt,
          params("month").toInt,
          params("day").toInt,
          params.getOrElse("hour", "-1").toInt,
          params.getOrElse("minute", "-1").toInt,
          params.getOrElse("second", "-1").toInt,
          params.getOrElse("milli", "-1").toInt
        )
      ).filter(_.validate)
        .getOrElse {
          logger.error("invalid_params_1={}", request.getQueryString)
          halt(BadRequest(EventLogGenericResponse(success = false, "Invalid query data provided", Nil)))
        }

    } catch {
      case e: Exception =>
        logger.error("invalid_params_2={}, error={}", request.getQueryString, e.getMessage)
        halt(BadRequest(EventLogGenericResponse(success = false, "Error reading query params", Nil)))
    }

    logger.debug("params={}", request.getQueryString)
    logger.debug("QueryByCatAndTimeElems={}", query)

    val res = eventsByCat.byCatAndTimeElems(
      query.category,
      query.year,
      query.month,
      query.day,
      query.hour,
      query.minute,
      query.second,
      query.milli,
      10
    )

    val promise = Promise[ActionResult]()

    res.onComplete {

      case Success(value) =>
        logger.info("events_query_result=:  " + value)
        val response = {
          EventLogGenericResponse(
            success = true,
            "Request successfully processed",
            value.map(x => EventLogRow.toEventLog(x))
          )
        }
        val r = if (value.isEmpty) NotFound(response) else Ok(response)

        promise.success(r)
      case Failure(exception) =>
        logger.error("Error querying:" + exception.getMessage)
        val r = InternalServerError(
          EventLogGenericResponse(
            success = false,
            "Something happened when processing this request.",
            Nil
          )
        )
        promise.success(r)
    }

    val finalRes = new AsyncResult() {
      override val is = promise.future
    }
    finalRes

  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(EventLogGenericResponse(success = false, "Route not found", Nil))
  }

}
