package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.classloader.{ TrustCodeLoad, TrustCodeLoader }
import com.ubirch.models._
import com.ubirch.sdk.EventLogging
import com.ubirch.service.eventLog.EventLogEndpoint
import com.ubirch.service.rest.ServerConfig
import com.ubirch.util.{ EventLogJsonSupport, UUIDHelper }
import javax.inject._
import org.json4s.Formats
import org.scalatra._
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }

import scala.concurrent.{ ExecutionContext, Future }
import scala.tools.reflect.ToolBoxError
import scala.util.{ Failure, Success, Try }

class EventLogTrustCodeController @Inject() (
    val swagger: Swagger,
    eventLogging: EventLogging,
    eventLogEndpoint: EventLogEndpoint,
    trustCodeLoader: TrustCodeLoader,
    cache: Cache,
    serverConfig: ServerConfig

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

  // Allows CORS support to display the swagger UI when using the same network
  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers")
    )
  }

  get("/ping") {
    Ok("PONG")
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

    logger.info("Received request with params {}", trustCodeCreation)

    val tc = Try(EventLogJsonSupport.FromJson[TrustCodeCreation](trustCodeCreation).get).getOrElse {
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
          .withNewId("TC." + uuid.toString)
          .withCategory(Values.UPP_CATEGORY)
          .withServiceClass("TRUST_CODE")
          .withRandomNonce
          .commitAsync
          .map { el =>
            Created(TrustCodeGenericResponse(
              success = true,
              "Trust Code Successfully created",
              List(TrustCodeResponse(el.id, serverConfig.createURL("/trust_code/" + el.id), Some(el)))
            ))
          }(executor)
      case Failure(e: ToolBoxError) =>
        logger.error("1. Error creating trust code={}", e.getMessage)
        Future.successful(BadRequest(TrustCodeGenericResponse(success = false, s"Error creating trust code=${e.getMessage} ", Nil)))
      case Failure(e) =>
        logger.error("2. Error creating trust code={}", e)
        Future.successful(InternalServerError(TrustCodeGenericResponse(success = false, s"Error creating trust code=${e.getMessage} ", Nil)))
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
        if (els.isEmpty) {
          NotFound(TrustCodeGenericResponse(
            success = true,
            "Trust Code Not Found",
            Nil
          ))
        } else {
          Ok(TrustCodeGenericResponse(
            success = true,
            "Trust Code Successfully retrieved",
            els.map(el => TrustCodeResponse(el.id, serverConfig.createURL("/trust_code/" + el.id), Some(el)))
          ))
        }

      }.recover {
        case e: Exception =>
          Future.successful(InternalServerError(TrustCodeGenericResponse(success = false, s"Error retrieving trust code=${e.getMessage} ", Nil)))

      }

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes
  }

  get("/trust_code/:id/verify") {
    val trustCodeId = params("id")
    TrustCodeGenericResponse(success = true, "Trust Code Successfully verified", Nil)
  }

  post("/trust_code/:id/:method") {
    val trustCodeId = params("id")
    val method = params("method")

    val methodParams = Try(EventLogJsonSupport.FromJson[List[TrustCodeMethodParam]](parsedBody).get).getOrElse {
      halt(BadRequest(
        TrustCodeGenericResponse(
          success = false,
          "Error parsing request",
          Nil
        )
      ))
    }

    val paramsTuple = methodParams.map { p =>
      p.tpe.toUpperCase match {
        case "STRING" => (classOf[String], p.value)
        case "INT" => (classOf[Int], p.value.asInstanceOf[Int])
        case "BOOLEAN" => (classOf[Boolean], p.value.asInstanceOf[Boolean])
        case "LONG" => (classOf[Long], p.value.asInstanceOf[Long])
        case "FLOAT" => (classOf[Float], p.value.asInstanceOf[Float])
        case _ =>
          halt(BadRequest(
            TrustCodeGenericResponse(
              success = false,
              "Type not supported. Only types supported: String, Int, Boolean, Long and Float.",
              Nil
            )
          ))
      }

    }

    val fres = cache.get[TrustCodeLoad](trustCodeId).map {

      case Some(TrustCodeLoad(_, instance, clazz)) =>

        val declaredMethods = clazz.getDeclaredMethods.toList

        val completeParams = (classOf[Context], Context(trustCodeId)) +: paramsTuple

        if (declaredMethods.nonEmpty && paramsTuple.isEmpty) {
          BadRequest(TrustCodeGenericResponse(success = false, "Trust Code Params Not Found", Nil))
        } else {

          try {
            if (declaredMethods.exists(_.getName == method)) {

              clazz
                .getDeclaredMethod(method, completeParams.map(_._1): _*)
                .invoke(instance, completeParams.map(_._2.asInstanceOf[Object]): _*)

              Ok(TrustCodeGenericResponse(success = true, "Trust Code Method Executed", Nil))
            } else
              NotFound(TrustCodeGenericResponse(success = false, "Trust Code Method Not Found", Nil))

          } catch {
            case e: Exception =>
              logger.error("Error Executing Trust Code Method {}", e.getMessage)
              InternalServerError(TrustCodeGenericResponse(success = false, "Error Executing Trust Code Method = " + e.getMessage, Nil))
          }
        }

      case None => NotFound(TrustCodeGenericResponse(success = false, "Trust Code has not been initiated", Nil))
    }

    val finalRes = new AsyncResult() {
      override val is = fres
    }
    finalRes

  }

  post("/trust_code/:id/init") {
    val trustCodeId = params("id")

    def run: Future[TrustCodeGenericResponse] = {
      eventLogEndpoint
        .queryByIdAndCat(trustCodeId, Values.UPP_CATEGORY)
        .flatMap { els =>

          try {
            els.headOption.map { x =>
              logger.info("Creating trust code")
              val trustCode = EventLogJsonSupport.FromJson[TrustCodeCreation](x.event).get
              trustCodeLoader.materialize(x.id, trustCode.trustCode) match {
                case Success(value) =>
                  cache.put[TrustCodeLoad](value.id, value).map { _ =>
                    TrustCodeGenericResponse(
                      success = true,
                      "Trust Code Successfully initiated",
                      els.map(el => TrustCodeResponse(el.id, serverConfig.createURL("/trust_code/" + el.id), Some(el)))
                    )
                  }
                case Failure(e) =>
                  logger.error("error={}", e.getMessage)
                  Future.successful {
                    TrustCodeGenericResponse(
                      success = false,
                      "Error initiating Trust Code: " + e.getMessage,
                      els.map(el => TrustCodeResponse(el.id, serverConfig.createURL("/trust_code/" + el.id), Some(el)))
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

          } catch {
            case e: Exception =>
              logger.error("error={}", e.getMessage)
              Future.successful {
                TrustCodeGenericResponse(
                  success = false,
                  "Error initiating Trust Code: " + e.getMessage,
                  els.map(el => TrustCodeResponse(el.id, serverConfig.createURL("/trust_code/" + el.id), Some(el)))
                )
              }
          }

        }
    }

    val fres = cache.get[TrustCodeLoad](trustCodeId).flatMap {
      case Some(t) =>

        Future.successful(TrustCodeGenericResponse(
          success = true,
          "Trust Code already initiated",
          Nil
        ))

      case None => run
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
