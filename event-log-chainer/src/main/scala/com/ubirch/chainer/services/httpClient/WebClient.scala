package com.ubirch.chainer.services.httpClient

import java.util.concurrent.Executor

import com.typesafe.scalalogging.LazyLogging
import javax.inject._
import org.asynchttpclient.Dsl._
import org.asynchttpclient.{ ListenableFuture, Response }

import scala.concurrent.{ ExecutionContext, Future, Promise }

trait WebClient {
  def get(url: String)(implicit exec: Executor): Future[Response]
  def post(url: String)(body: Array[Byte])(implicit exec: Executor): Future[Response]
}

@Singleton
class DefaultAsyncWebClient extends WebClient with LazyLogging {

  private val client = asyncHttpClient()

  def futureFromPromise(f: ListenableFuture[Response])(implicit exec: Executor) = {
    val p = Promise[Response]()
    f.addListener(new Runnable {
      def run = {
        try {
          p.success(f.get)
        } catch {
          case e: Exception =>
            p.failure(e)
        }
      }
    }, exec)
    p.future
  }
  def get(url: String)(implicit exec: Executor): Future[Response] = {
    val f = client.prepareGet(url).execute()
    futureFromPromise(f)
  }

  def post(url: String)(body: Array[Byte])(implicit exec: Executor): Future[Response] = {
    val f = client.preparePost(url).setBody(body).execute()
    futureFromPromise(f)
  }

  def shutdown(): Unit = client.close()

}

object WebClientTest extends App {

  implicit val exec = scala.concurrent.ExecutionContext.global.asInstanceOf[Executor with ExecutionContext]
  val webClient = new DefaultAsyncWebClient
  webClient.get("http://www.google.com/").map(x => println(x.getResponseBody)).foreach(_ => webClient.shutdown())
}
