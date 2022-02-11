package com.ubirch.verification.controllers

import java.util.{ Date, UUID }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.api.{ Claims, InvalidClaimException, TokenSDKException }
import com.ubirch.defaults.TokenApi
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.controllers.Api.{ AuthorizationHeaderNotFound, DecoratedResponse, Failure, Forbidden, NotFound, Response, Success }
import com.ubirch.verification.models.AcctEvent
import com.ubirch.verification.services.kafka.AcctEventPublishing
import io.prometheus.client.{ Counter, Summary }
import io.udash.rest.raw.HttpErrorException
import monix.eval.Task
import monix.execution.Scheduler
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.{ ExecutionContext, Future, TimeoutException }
import scala.util.control.NoStackTrace
import scala.concurrent.duration._
import scala.language.postfixOps

class ControllerHelpers(accounting: AcctEventPublishing)(implicit val ec: ExecutionContext) extends LazyLogging {

  implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  private val processingTimer: Summary = Summary
    .build("processing_time_seconds", "Message processing time in seconds")
    .labelNames("service", "end_point")
    .quantile(0.9, 0.05)
    .quantile(0.95, 0.05)
    .quantile(0.99, 0.05)
    .quantile(0.999, 0.05)
    .register()

  private val requestReceived: Counter = Counter
    .build("http_requests_count", "Number of http request received.")
    .register()

  private val responsesSent: Counter = Counter
    .build("http_responses_count", "Number of http responses sent.")
    .labelNames("status")
    .register()

  private[controllers] def registerMetrics[T](endpoint: String)(f: () => Future[T]): Future[T] = {
    val timer = processingTimer.labels("verification", endpoint).startTimer()
    f().transform { r => requestReceived.inc(); timer.observeDuration(); r }
      .transform { r =>
        r match {
          case util.Success(value: Response) =>
            value match {
              case Success(_, _, _) => responsesSent.labels(Response.OK.toString).inc()
              case NotFound => responsesSent.labels(Response.NOT_FOUND.toString).inc()
              case AuthorizationHeaderNotFound => responsesSent.labels(Response.UNAUTHORIZED.toString).inc()
              case Forbidden => responsesSent.labels(Response.FORBIDDEN.toString).inc()
              case Failure(_, _, _, _) => responsesSent.labels(Response.BAD_REQUEST.toString).inc()

            }
          case util.Success(_) => responsesSent.labels(Response.OK.toString).inc()
          case util.Failure(_) => responsesSent.labels(Response.BAD_REQUEST.toString).inc()
        }
        r
      }

  }

  private[controllers] def publishAcctEvent(ownerId: UUID, protocolMessage: ProtocolMessage, claims: Claims): Task[RecordMetadata] = {
    accounting
      .publish(AcctEvent(
        id = UUID.randomUUID(),
        ownerId = ownerId,
        identityId = Option(protocolMessage.getUUID),
        category = "verification",
        subCategory = None,
        token = Some(claims.token),
        occurredAt = new Date()
      ))
  }

  private def validateClaims(claims: Claims, upp: ProtocolMessage, origin: String) = {
    for {
      owner <- Task.fromTry(claims.isSubjectUUID)
      _ <- Task.fromTry(claims.validateOrigin(Option(origin).filterNot(_ == "No-Header-Found")))
      _ <- if (claims.hasMaybeTargetIdentities) Task.fromTry(claims.validateIdentity(upp.getUUID)) else Task.delay(upp.getUUID)
      exRes <- if (claims.hasMaybeGroups) TokenApi.externalStateVerify(claims.token, upp.getUUID) else Task.delay(true)
      _ <- if (!exRes) Task.raiseError(InvalidClaimException("Invalid Validation", "External Validation Failed")) else Task.unit
    } yield owner
  }

  private[controllers] def validateClaimsAndRegisterAcctEvent[T](origin: String, claims: Claims)(f: => Future[DecoratedResponse]): Future[Response] = {

    f.flatMap { decoratedResponse =>

      decoratedResponse.protocolMessage.map { upp =>

        (for {
          owner <- validateClaims(claims, upp, origin).timeout(5 seconds)
          _ <- if (decoratedResponse.isSuccess) publishAcctEvent(owner, upp, claims).map(x => Some(x)).timeout(5 seconds)
          else Task.delay(None)
        } yield decoratedResponse.response).onErrorRecover {

          case exception: TimeoutException =>
            logger.error("request_timed_out=" + exception.getMessage)
            Failure()

          case exception: TokenSDKException =>
            logger.error("token_sdk_exception=" + exception.getMessage, exception)
            Forbidden

          case exception =>
            logger.error("unexpected_error=" + exception.getMessage, exception)
            Forbidden

        }

      }.getOrElse {
        Task.delay(decoratedResponse.response)
      }.runToFuture

    }

  }

  private[controllers] def authorization[T](authToken: String)(f: Claims => Future[Response]): () => Future[Response] = {
    lazy val claims = TokenApi.getClaims(authToken)
    authToken match {
      case "No-Header-Found" => () => Future.successful(AuthorizationHeaderNotFound)
      case _ if claims.isSuccess => () => f(claims.get)
      case _ => () => Future.successful(Forbidden)
    }
  }

  /** little utility function that makes the DSL below readable */
  private[controllers] def earlyResponseIf(condition: Boolean)(response: => DecoratedResponse): Future[Unit] =
    if (condition) Future.failed(ResponseException(response)) else Future.unit

  /** exception for wrapping early responses in the DSL below */
  case class ResponseException(resp: DecoratedResponse) extends Exception with NoStackTrace

  private[controllers] def finalizeResponse(responseFuture: Future[DecoratedResponse], requestId: String): Future[DecoratedResponse] =
    responseFuture.recover {
      case ResponseException(response) => response
      case e: Exception =>
        logger.error(s"[$requestId] unexpected exception", e)
        throw HttpErrorException(500, payload = s"InternalServerError: ${e.getClass.getSimpleName}: ${e.getMessage}", cause = e)
    }

}
