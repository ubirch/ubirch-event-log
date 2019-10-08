package com.ubirch.controllers

import com.ubirch.models.GenericResponse
import com.ubirch.util.EventLogJsonSupport
import org.json4s.Formats
import org.json4s.JsonAST.JString
import org.scalatra.json.NativeJsonSupport
import org.scalatra.swagger.{ Swagger, SwaggerSupport, SwaggerSupportSyntax }
import org.scalatra.{ CorsSupport, ScalatraServlet }

class EventLogController(implicit val swagger: Swagger) extends ScalatraServlet
  with NativeJsonSupport with SwaggerSupport with CorsSupport {

  override protected def applicationDescription: String = "EventLog Getter"
  override protected implicit def jsonFormats: Formats = EventLogJsonSupport.formats

  val holaSwagger: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[GenericResponse]("info")
      summary "Returns the basic info of the API"
      description "Description ...."
      tags "info"
      parameters ())

  get("/", operation(holaSwagger)) {
    GenericResponse.Success("Successfully Processed", JString("que mas"))
  }

}
