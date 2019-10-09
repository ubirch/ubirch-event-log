package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models._
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Exceptions.ExecutionException
import javax.inject._
import org.json4s.Formats
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra._

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success }

class EventLogController @Inject() (val swagger: Swagger, eventsByCat: EventsByCat)(implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging
  with FutureSupport {

  override protected def applicationDescription: String = "EventLog Getter"
  override protected implicit def jsonFormats: Formats = EventLogJsonSupport.formats

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

  val eventsSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[EventLogGenericResponse]("Returns EventLog based on category and time elements.")
      summary "Queries for event logs that have a common category and time elements."
      description "Queries for event logs that have a common category and time elements."
      tags "Queries for event logs"
      parameters
      bodyParam[QueryByCatAndTimeElems]("query"))

  post("/events", operation(eventsSwagger)) {

    val query = parsedBody
      .extractOpt[QueryByCatAndTimeElems]
      .filter(_.validate)
      .getOrElse(halt(BadRequest(EventLogGenericResponse(success = false, "Invalid query data provided", Nil))))

    val res = eventsByCat.byCatAndYearAndMonthAndDay(query.category, query.year, query.month, query.day)

    val promise = Promise[ActionResult]()

    res.onComplete {

      case Success(value) =>
        logger.info("Result:  " + value)
        val r = Ok(
          EventLogGenericResponse(
            success = true,
            "Request successfully processed",
            value.map(x => EventLogRow.toEventLog(x))
          )
        )
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

}
