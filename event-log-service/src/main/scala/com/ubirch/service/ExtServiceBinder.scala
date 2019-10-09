package com.ubirch.service

import com.google.inject.{ AbstractModule, Module }
import com.ubirch.service.swagger.SwaggerProvider
import com.ubirch.services.kafka.consumer.DefaultStringConsumer
import org.scalatra.swagger.Swagger

class ExtServiceBinder
  extends AbstractModule {

  def configure(): Unit = {
    bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
  }

}

object ExtServiceBinder {
  val modules: List[Module] = List(new ExtServiceBinder)
}
