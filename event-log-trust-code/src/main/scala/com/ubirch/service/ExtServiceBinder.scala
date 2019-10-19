package com.ubirch.service

import com.google.inject.{AbstractModule, Module}
import com.ubirch.service.httpClient.{DefaultAsyncWebClient, WebClient}
import com.ubirch.service.swagger.SwaggerProvider
import org.scalatra.swagger.Swagger

class ExtServiceBinder
  extends AbstractModule {

  def configure(): Unit = {
    bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
    bind(classOf[WebClient]).to(classOf[DefaultAsyncWebClient])
  }

}

object ExtServiceBinder {
  val modules: List[Module] = List(new ExtServiceBinder)
}
