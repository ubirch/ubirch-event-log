package com.ubirch.controllers

import javax.inject._
import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ ApiInfo, NativeSwaggerBase, Swagger }

class ResourcesController @Inject() (val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object RestApiInfo extends ApiInfo(
  "Event Log Service",
  "These are the available endpoints for querying the Event Log Service",
  "https://ubirch.de",
  "carlos.sanchez@ubirch.com",
  "Apache License, Version 2.0",
  "https://www.apache.org/licenses/LICENSE-2.0"
)
