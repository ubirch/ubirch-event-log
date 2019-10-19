package com.ubirch.controllers

import java.text.SimpleDateFormat
import java.time.DateTimeException

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models._
import com.ubirch.sdk.{EventLogging, EventLoggingBase}
import com.ubirch.service.eventLog.EventLogEndpoint
import com.ubirch.util.EventLogJsonSupport
import javax.inject._
import org.joda.time.DateTime
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

class EventLogTrustCodeController @Inject()(val swagger: Swagger,
                                            eventLogging: EventLogging,
                                            eventLogEndpoint: EventLogEndpoint)
                                           (implicit val executor: ExecutionContext)
  extends ScalatraServlet
  with NativeJsonSupport
  with SwaggerSupport
  with CorsSupport
  with LazyLogging
  with FutureSupport {

  override protected def applicationDescription: String = "EventLog Trust Code"
  override protected implicit def jsonFormats: Formats = EventLogJsonSupport.formats

  before() {
    contentType = formats("json")
  }

  val trustCodeSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[TrustCodeResponse]("Returns EventLog based on category and time elements.")
      summary "Queries for event logs that have a common category and time elements."
      description "Queries for event logs that have a common category and time elements."
      tags "Queries for event logs"
      parameters bodyParam[TrustCodeCreation])

  post("/trust_code", operation(trustCodeSwagger)) {
    import eventLogging._
    val trustCodeCreation = parsedBody

    val fres = eventLogging.log(trustCodeCreation)
      .withNewId
      .withCategory(Values.UPP_CATEGORY)
      .withServiceClass("SMART_CODE")
      .withRandomNonce
      .commitAsync
      .map { el =>
        Ok(TrustCodeGenericResponse(
          success = true,
          "Trust Code Successfully created",
          List(TrustCodeResponse(el.id, "http://localhost:8081/v1/trust_code/" + el.id, Some(el)))
        ))
      }(executor)

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes

  }


  get("/trust_code/:id") {
    val trustCodeId = params("id")
    val fres = eventLogEndpoint
      .queryByIdAndCat(trustCodeId, Values.UPP_CATEGORY)
      .map{ els =>
      TrustCodeGenericResponse(
        success = true,
        "Trust Code Successfully retrieved",
        els.map(el => TrustCodeResponse(el.id, "http://localhost:8081/v1/trust_code/" + el.id, Some(el)))
      )
    }

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes
  }

  get("/trust_code/:id/init") {
    val trustCodeId = params("id")
    val fres = eventLogEndpoint
      .queryByIdAndCat(trustCodeId, Values.UPP_CATEGORY)
      .map{ els =>
        TrustCodeGenericResponse(
          success = true,
          "Trust Code Successfully init",
          els.map(el => TrustCodeResponse(el.id, "http://localhost:8081/v1/trust_code/" + el.id, Some(el)))
        )
      }

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes
  }

  get("/trust_code/:id/verify") {
    val trustCodeId = params("id")
    TrustCodeGenericResponse(true, "Trust Code Successfully verified", Nil)
  }

  post("/trust_code/:id/:method") {
    val trustCodeId = params("id")
    val method = params("method")
    val methodParams = parsedBody

    TrustCodeGenericResponse(true, "Trust Code Successfully verified", Nil)
  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(EventLogGenericResponse(success = false, "Route not found", Nil))
  }

}
