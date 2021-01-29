package com.ubirch.verification.services

import java.net.URL
import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.util.Exceptions.{ InvalidAllClaims, InvalidOrigin, InvalidOtherClaims, InvalidSpecificClaim, InvalidUUID }
import com.ubirch.verification.util.LookupJsonSupport

import javax.inject.{ Inject, Singleton }

import pdi.jwt.{ Jwt, JwtAlgorithm }

import scala.util.{ Failure, Success, Try }

case class Content(
    role: Symbol,
    purpose: String,
    targetIdentities: Either[List[UUID], String],
    originDomains: List[URL]
)
case class Claims(token: String, all: Map[String, Any], content: Content) {

  def validateUUID(protocolMessage: ProtocolMessage): Try[ProtocolMessage] = {
    val res = content.targetIdentities match {
      case Left(uuids) => uuids.contains(protocolMessage.getUUID)
      case Right(wildcard) => wildcard == "*"
    }
    if (res) Success(protocolMessage)
    else Failure(InvalidUUID("Invalid UUID", s"upp_uuid_not_equals_target_identities=${protocolMessage.getUUID} ${content.targetIdentities}"))
  }

  def validateOrigin(maybeOrigin: Option[String]): Try[List[URL]] = {
    (for {
      origin <- Try(maybeOrigin.filter(_.nonEmpty).map(x => new URL(x)))
      res <- Try(content.originDomains.forall(x => Option(x) == origin))
    } yield res) match {
      case Success(true) => Success(content.originDomains)
      case _ =>
        Failure(InvalidOrigin("Invalid Origin", s"origin_not_equals_origin_domains=${maybeOrigin.getOrElse("NO-ORIGIN")} - ${content.originDomains.map(_.toString)}"))
    }
  }

}

trait TokenVerification {
  def decodeAndVerify(jwt: String): Option[Claims]
}

object TokenVerification {
  final val ISSUER = "iss"
  final val SUBJECT = "sub"
  final val AUDIENCE = "aud"
  final val EXPIRATION = "exp"
  final val NOT_BEFORE = "nbf"
  final val ISSUED_AT = "iat"
  final val JWT_ID = "jti"

  implicit class EnrichedAll(all: Map[String, Any]) {
    def getSubject: Try[UUID] = all.get(SUBJECT).toRight(InvalidSpecificClaim("Invalid subject", all.toString()))
      .toTry
      .map(_.asInstanceOf[String])
      .filter(_.nonEmpty)
      .map(UUID.fromString)
      .recover { case e: Exception => throw InvalidSpecificClaim("Invalid subject", e.getMessage) }

  }

}

@Singleton
class DefaultTokenVerification @Inject() (config: Config, tokenPublicKey: TokenPublicKey) extends TokenVerification with LazyLogging {

  import TokenVerification._

  private val validIssuer = config.getString("verification.jwt.issuer")
  private val validAudience = config.getString("verification.jwt.audience")
  private val validRoles = config.getString("verification.jwt.roles")
    .replace(" ", "")
    .split(",")
    .toSet
    .map(x => Symbol(x))

  def decodeAndVerify(jwt: String): Option[Claims] = {
    (for {
      (_, p, _) <- Jwt.decodeRawAll(jwt, tokenPublicKey.publicKey, Seq(JwtAlgorithm.ES256))
      otherClaims <- Try(LookupJsonSupport.FromString[Content](p).get)
        .recover { case e: Exception => throw InvalidOtherClaims(e.getMessage, jwt) }

      all <- Try(LookupJsonSupport.FromString[Map[String, Any]](p).get)
        .recover { case e: Exception => throw InvalidAllClaims(e.getMessage, jwt) }

      isIssuerValid <- all.get(ISSUER).toRight(InvalidSpecificClaim("Invalid issuer", p)).toTry.map(_ == validIssuer)
      _ = if (!isIssuerValid) throw InvalidSpecificClaim("Invalid issuer", p)

      isAudienceValid <- all.get(AUDIENCE).toRight(InvalidSpecificClaim("Invalid audience", p)).toTry.map(_ == validAudience)
      _ = if (!isAudienceValid) throw InvalidSpecificClaim("Invalid audience", p)

      _ <- all.get(SUBJECT).toRight(InvalidSpecificClaim("Invalid subject", p))
        .toTry
        .map(_.asInstanceOf[String])
        .filter(_.nonEmpty)
        .map(UUID.fromString)
        .recover { case e: Exception => throw InvalidSpecificClaim(e.getMessage, p) }

      _ <- Try(otherClaims.role).filter(validRoles.contains)
        .recover { case e: Exception => throw InvalidSpecificClaim(e.getMessage, p) }

      _ <- Try(otherClaims.purpose).filter(_.nonEmpty)
        .recover { case e: Exception => throw InvalidSpecificClaim(e.getMessage, p) }

    } yield {
      Some(Claims(jwt, all, otherClaims))
    }).recover {
      case e: InvalidSpecificClaim =>
        logger.error(s"invalid_token_specific_claim=${e.getMessage}", e)
        None
      case e: InvalidAllClaims =>
        logger.error(s"invalid_token_all_claims=${e.getMessage}", e)
        None
      case e: InvalidOtherClaims =>
        logger.error(s"invalid_token_other_claims=${e.getMessage}", e)
        None
      case e: Exception =>
        logger.error(s"invalid_token=${e.getMessage}", e)
        None
    }.getOrElse(None)

  }

}
