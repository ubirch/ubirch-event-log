package com.ubirch.verification.service.utils.udash

import com.typesafe.scalalogging.StrictLogging
import com.ubirch.verification.service.Api
import io.udash.rest.RestServlet
import io.udash.rest.RestServlet.{ DefaultHandleTimeout, DefaultMaxPayloadSize }
import io.udash.rest.openapi.OpenApi
import io.udash.rest.raw.RawRest.HandleRequest
import io.udash.rest.raw.{ HttpMethod, PlainValue, RawRest, RestMetadata }
import javax.inject.Inject
import org.eclipse.jetty
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler, ServletHolder }

import scala.concurrent.duration.FiniteDuration

class JettyServer @Inject() (api: Api, docs: OpenApi, port: Int) extends StrictLogging {

  def corsAware(handle: HandleRequest): HandleRequest = request => {
    if (request.method == HttpMethod.OPTIONS)
      RawRest.mapAsync(handle(request)) { response =>
        response.copy(
          headers = response.headers
            .append("Access-Control-Allow-Origin", PlainValue("*"))
            .append("Access-Control-Allow-Methods", PlainValue(HttpMethod.OPTIONS.name + ", " + HttpMethod.POST.name))
            .append("Access-Control-Allow-Headers", PlainValue("*"))
        )
      }
    else
      handle(request)

  }

  def rest[RestApi: RawRest.AsRawRpc: RestMetadata](
      apiImpl: RestApi,
      handleTimeout: FiniteDuration = DefaultHandleTimeout,
      maxPayloadSize: Long = DefaultMaxPayloadSize
  ): RestServlet = {
    val handler: RawRest.HandleRequest = corsAware(RawRest.asHandleRequest[RestApi](apiImpl))
    new RestServlet(handler, handleTimeout, maxPayloadSize)
  }

  private val userApiServlet = rest(api)
  private val server = new jetty.server.Server(port)
  private val servletHandler = new ServletContextHandler

  val swaggerPath = "/swagger" // if this is changed, `resources/swagger/index.html` also has to be tweaked

  def startAndJoin(): Unit = {
    servletHandler.addServlet(new ServletHolder(userApiServlet), "/api/*")
    addSwagger(docs, swaggerPath)

    server.setHandler(servletHandler)
    server.start()
    server.join()
  }

  private def addSwagger(openApi: OpenApi, swaggerPrefix: String): Unit = {
    // add swagger static files
    val swaggerResourceUrl = getClass.getClassLoader.getResource("swagger/index.html").toString.stripSuffix("index.html")

    logger.debug(s"swagger root url: $swaggerResourceUrl")

    val swaggerStaticServletHolder = new ServletHolder("swagger", classOf[DefaultServlet])
    swaggerStaticServletHolder.setInitParameter("pathInfoOnly", "true")
    swaggerStaticServletHolder.setInitParameter("resourceBase", swaggerResourceUrl)

    servletHandler.addServlet(swaggerStaticServletHolder, s"$swaggerPrefix/*")

    // add the dynamically generated swagger.json
    val swaggerJsonServlet = RestServlet[SwaggerJsonApi](new SwaggerJsonApi.Impl(openApi))
    servletHandler.addServlet(new ServletHolder(swaggerJsonServlet), s"$swaggerPrefix/swagger.json")
  }
}

