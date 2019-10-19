package com.ubirch.service.swagger

import com.ubirch.controllers.RestApiInfo
import javax.inject._
import org.scalatra.swagger.Swagger

@Singleton
class SwaggerProvider extends Provider[Swagger] {
  lazy val swagger = new Swagger(Swagger.SpecVersion, "1.3.0", RestApiInfo)
  override def get(): Swagger = swagger
}
