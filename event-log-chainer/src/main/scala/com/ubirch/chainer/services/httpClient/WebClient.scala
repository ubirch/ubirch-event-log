package com.ubirch.chainer.services.httpClient

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
  def fromResponse(response: Response) = WebclientResponse(response.getStatusText, response.getStatusCode, response.getContentType, response.getResponseBody, response.getResponseBodyAsStream)
}

trait WebClient {
  def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse]
}

@Singleton
class DefaultAsyncWebClient extends WebClient with LazyLogging {

  private val client = asyncHttpClient()

  def futureFromPromise(f: ListenableFuture[Response])(implicit exec: Executor) = {
    val p = Promise[WebclientResponse]()
    f.addListener(new Runnable {
      def run = {
        try {
          p.success(WebclientResponse.fromResponse(f.get))
        } catch {
          case e: Exception =>
            logger.error("Something went wrong when talking to the event log service endpoint")
            p.failure(e)
        }
      }
    }, exec)
    p.future
  }

  def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse] = {
    val f = client.prepareGet(url).setQueryParams(params.asJava).execute()
    futureFromPromise(f)
  }

  def shutdown(): Unit = client.close()

}

object WebClientTest extends App {

  implicit val exec = scala.concurrent.ExecutionContext.global.asInstanceOf[Executor with ExecutionContext]
  val webClient = new DefaultAsyncWebClient
  webClient.get("http://www.google.com/")(Nil).map(x => println(x.getResponseBody)).foreach(_ => webClient.shutdown())
}
