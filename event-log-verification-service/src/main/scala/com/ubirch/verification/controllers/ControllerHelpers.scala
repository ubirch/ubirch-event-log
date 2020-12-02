package com.ubirch.verification.controllers

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.verification.controllers.Api.{ AuthorizationHeaderNotFound, DecoratedResponse, Failure, Forbidden, NotFound, Response, Success }
import com.ubirch.verification.models.AcctEvent
import com.ubirch.verification.services.kafka.AcctEventPublishing
import com.ubirch.verification.services.{ Content, TokenVerification }
import com.ubirch.verification.util.Exceptions.InvalidSpecificClaim
import io.prometheus.client.{ Counter, Summary }
import io.udash.rest.raw.HttpErrorException

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NoStackTrace

class ControllerHelpers(accounting: AcctEventPublishing, tokenVerification: TokenVerification)(implicit val ec: ExecutionContext) extends LazyLogging {

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

  private[controllers] def registerAcctEvent[T](accessInfo: (Map[String, Any], Content))(f: => Future[DecoratedResponse]): Future[Response] = {
    import TokenVerification._

    val (all, content) = accessInfo

    f.transform { r =>

      val res: scala.util.Try[Response] = for {
        owner <- all.getSubject
        DecoratedResponse(maybeUPP, response) <- r
      } yield {

        maybeUPP match {
          case Some(upp) =>
            if (content.targetIdentities.contains(upp.getUUID.toString)) {

              response match {
                case Success(_, _, _) =>
                  accounting
                    .publish_!(AcctEvent(java.util.UUID.randomUUID(), owner, Option(upp.getUUID), "verification", Some(content.purpose), new Date()))
                case _ => // Do nothing

              }

              response

            } else {
              logger.warn("upp_uuid_not_equals_target_identities {} {}", upp.getUUID, content.targetIdentities)
              Forbidden
            }
          case None => response
        }

      }

      res.recover {
        case e: InvalidSpecificClaim =>
          logger.error(s"error_getting_owner_token=${e.getMessage}", e)
          Forbidden
        case e: Exception =>
          logger.error(s"unknown_token=${e.getMessage}", e)
          Failure()
      }

    }

  }

  private def getToken(token: String): Option[(Map[String, Any], Content)] = token.split(" ").toList match {
    case List(x, y) =>
      val isBearer = x.toLowerCase == "bearer"
      val token = tokenVerification.decodeAndVerify(y)
      if (isBearer && token.isDefined) {
        token
      } else {
        None
      }

    case _ => None
  }

  private[controllers] def authorization[T](authToken: String)(f: ((Map[String, Any], Content)) => Future[Response]): () => Future[Response] = {
    lazy val token = getToken(authToken)
    authToken match {
      case "No-Header-Found" => () => Future.successful(AuthorizationHeaderNotFound)
      case _ if token.isDefined => () => f(token.get)
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
