package com.ubirch.chainer.services.httpClient

import com.ubirch.services.lifeCycle.{ DefaultLifecycle, Lifecycle }

import java.io.InputStream
import java.util.concurrent.Executor
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import org.asynchttpclient.Dsl._
import org.asynchttpclient.{ ListenableFuture, Param, Response }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future, Promise }

case class WebclientResponse(
    getStatusText: String,
    getStatusCode: Int,
    getContentType: String,
    getResponseBody: String,
    getResponseBodyAsStream: InputStream
)

object WebclientResponse {
  def fromResponse(response: Response): WebclientResponse = {
    WebclientResponse(
      getStatusText = response.getStatusText,
      getStatusCode = response.getStatusCode,
      getContentType = response.getContentType,
      getResponseBody = response.getResponseBody,
      getResponseBodyAsStream = response.getResponseBodyAsStream
    )
  }
}

trait WebClient {
  def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse]
}

@Singleton
class DefaultAsyncWebClient @Inject() (lifecycle: Lifecycle) extends WebClient with LazyLogging {

  private val client = asyncHttpClient()

  private def futureFromPromise(f: ListenableFuture[Response])(implicit exec: Executor): Future[WebclientResponse] = {
    val p = Promise[WebclientResponse]()
    f.addListener(new Runnable {
      def run = {
        try {
          val res = f.get
          logger.info("response={}", res.getResponseBody)
          p.success(WebclientResponse.fromResponse(res))
        } catch {
          case e: Exception =>
            logger.error("Something went wrong when talking to the event log service endpoint")
            p.failure(e)
        }
      }
    }, exec)
    p.future
  }

  override def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse] = {
    val f = client.prepareGet(url).setQueryParams(params.asJava).execute()
    futureFromPromise(f)
  }

  def shutdown(): Unit = client.close()

  lifecycle.addStopHook { () =>
    logger.debug("Shutting down web client")
    Future.successful(shutdown())
  }

}

object WebClientTest extends App {

  implicit val exec = scala.concurrent.ExecutionContext.global.asInstanceOf[Executor with ExecutionContext]
  val lifecycle = new DefaultLifecycle()
  val webClient = new DefaultAsyncWebClient(lifecycle)
  webClient.get("https://www.google.com/")(Nil).map(x => println(x.getResponseBody)).foreach(_ => webClient.shutdown())
}
