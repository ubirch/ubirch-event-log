package com.ubirch.controllers

import java.text.SimpleDateFormat
import java.time.DateTimeException

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.classloader.{TrustCodeLoad, TrustCodeLoader}
import com.ubirch.models._
import com.ubirch.sdk.{EventLogging, EventLoggingBase}
import com.ubirch.service.eventLog.EventLogEndpoint
import com.ubirch.util.{EventLogJsonSupport, UUIDHelper}
import javax.inject._
import org.joda.time.DateTime
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport, SwaggerSupportSyntax}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class EventLogTrustCodeController @Inject() (
    val swagger: Swagger,
    eventLogging: EventLogging,
    eventLogEndpoint: EventLogEndpoint,
    trustCodeLoader: TrustCodeLoader,
    cache: Cache
)
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

    val tc = Try(EventLogJsonSupport.FromJson[TrustCodeCreation](trustCodeCreation).get).getOrElse{
      halt(BadRequest(
        TrustCodeGenericResponse(
          success = false,
          "Error parsing request",
          Nil
        )
      ))
    }

    val uuid = UUIDHelper.randomUUID
    val fres = trustCodeLoader.materialize(uuid.toString, tc.trustCode) match {
      case Success(_) =>
        eventLogging.log(trustCodeCreation)
          .withNewId(uuid)
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
      case Failure(e) =>
        logger.error("Error creating trust code {}", e.getMessage)
        Future.successful(Ok(TrustCodeGenericResponse(success = false, s"Error creating trust code=${e.getMessage} ", Nil)))
    }

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes

  }

  get("/trust_code/:id") {
    val trustCodeId = params("id")
    val fres = eventLogEndpoint
      .queryByIdAndCat(trustCodeId, Values.UPP_CATEGORY)
      .map { els =>
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
    val fres: Future[TrustCodeGenericResponse] = eventLogEndpoint
      .queryByIdAndCat(trustCodeId, Values.UPP_CATEGORY)
      .flatMap { els =>

        els.headOption.map { x =>
          logger.info("Creating trust code")
          val trustCode = EventLogJsonSupport.FromJson[TrustCodeCreation](x.event).get
          trustCodeLoader.materialize(x.id, trustCode.trustCode) match {
            case Success(value) =>
              cache.put[TrustCodeLoad](value.id, value).map {_  =>
                TrustCodeGenericResponse(
                  success = true,
                  "Trust Code Successfully initiated",
                  els.map(el => TrustCodeResponse(el.id, "http://localhost:8081/v1/trust_code/" + el.id, Some(el)))
                )
              }
            case Failure(exception) =>
              logger.error("error={}", exception.getMessage)
              Future.successful {
                TrustCodeGenericResponse(
                  success = false,
                  "Error initiating Trust Code: " + exception.getMessage,
                  els.map(el => TrustCodeResponse(el.id, "http://localhost:8081/v1/trust_code/" + el.id, Some(el)))
                )
              }
          }
        }.getOrElse {
          Future.successful {
            TrustCodeGenericResponse(
              success = false,
              "No Trust Code found",
              Nil
            )
          }
        }
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

    val methodParams = Try(EventLogJsonSupport.FromJson[List[TrustCodeMethodParam]](parsedBody).get).getOrElse{
      halt(BadRequest(
        TrustCodeGenericResponse(
          success = false,
          "Error parsing request",
          Nil
        )
      ))
    }

    val fres = cache.get[TrustCodeLoad](trustCodeId).map {

      case Some(TrustCodeLoad(id, instance, clazz)) =>

        val params = methodParams.map { p =>
          p.tpe match {
            case "STRING" => (classOf[String], p.value)
            case "INT" => (classOf[Int], p.value.asInstanceOf[Int])
            case "BOOLEAN" => (classOf[Boolean], p.value.asInstanceOf[Boolean])
            case "LONG" => (classOf[Long], p.value.asInstanceOf[Long])
            case "FLOAT" => (classOf[Float], p.value.asInstanceOf[Float])
          }

        }

        try {
          if (clazz.getDeclaredMethods.exists(_.getName == method)) {
            clazz.getDeclaredMethod(method, params.map(_._1): _*).invoke(instance, params.map(_._2.asInstanceOf[Object]): _*)
            Ok(TrustCodeGenericResponse(true, "Trust Code Method Executed", Nil))

          } else {
            NotFound(TrustCodeGenericResponse(false, "Trust Code Method Not Found", Nil))
          }

        } catch {
          case e: Exception =>
            logger.error("Error Executing Trust Code Method {}", e.getMessage)
            InternalServerError(TrustCodeGenericResponse(false, "Error Executing Trust Code Method = " + e.getMessage, Nil))
        }

      case None => NotFound(TrustCodeGenericResponse(false, "Trust Code has not been initiated", Nil))
    }


    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes


  }

  notFound {
    logger.info("route_not_found={} query_string={}", requestPath, request.getQueryString)
    NotFound(EventLogGenericResponse(success = false, "Route not found", Nil))
  }

}
