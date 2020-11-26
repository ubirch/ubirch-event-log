package com.ubirch.verification.services

import java.time.Clock
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.crypto.utils.Curve
import com.ubirch.verification.util.LookupJsonSupport
import javax.inject.{ Inject, Singleton }
import org.bouncycastle.util.encoders.Hex
import pdi.jwt.{ Jwt, JwtAlgorithm, JwtClaim }

import scala.util.Try

case class Content(role: Symbol, purpose: String)

trait TokenCreation {
  def encode(jwtClaim: JwtClaim, privKey: PrivKey): Try[String]
  def encode(by: String, to: String, about: String, expiresIn: Option[Int], otherClaims: Content, privKey: PrivKey): Try[String]
}

@Singleton
class DefaultTokenCreation @Inject() () extends TokenCreation with LazyLogging {

  implicit private val clock: Clock = Clock.systemUTC

  def encode(jwtClaim: JwtClaim, privKey: PrivKey): Try[String] = Try {
    Jwt.encode(
      jwtClaim,
      privKey.getPrivateKey,
      JwtAlgorithm.ES256
    )
  }

  override def encode(by: String, to: String, about: String, expiresIn: Option[Int], otherClaims: Content, privKey: PrivKey): Try[String] = {
    for {

      oc <- Try(LookupJsonSupport.ToJson(otherClaims).toString)

      jwtClaim <- Try {
        JwtClaim(oc)
          .by(by)
          .to(to)
          .about(about)
          .issuedNow
          .withId(UUID.randomUUID().toString)
      }.map { x => expiresIn.map(x.expiresIn(_)).getOrElse(x) }

      token <- encode(jwtClaim, privKey)
    } yield {
      token
    }
  }

}

object DefaultTokenCreation {

  def main(args: Array[String]): Unit = {

    def go(rawPrivKeyAsHex: String) = for {
      privKey <- Try(GeneratorKeyFactory.getPrivKey(rawPrivKeyAsHex, Curve.PRIME256V1))
      creation: TokenCreation = new DefaultTokenCreation()
      token <- creation.encode(
        by = "https://token.dev.ubirch.com",
        to = UUID.randomUUID().toString,
        about = "https://verify.dev.ubirch.com",
        expiresIn = Some(631139040),
        Content('tester_verifier, "Lara del Rey Concert"),
        privKey
      )

    } yield {
      token
    }

    args.toList.filter(_.nonEmpty) match {
      case List(pr) => go(pr).foreach(print)
      case _ =>
        println("Generating key pair: ECDSA with PRIME256V1")

        val privKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)
        println("private_key:" + Hex.toHexString(privKey.getPrivateKey.getEncoded))
        println("private_key_raw:" + Hex.toHexString(privKey.getRawPrivateKey))
        println("pubkey_key:" + Hex.toHexString(privKey.getPublicKey.getEncoded))
        println("pubkey_key_raw:" + Hex.toHexString(privKey.getRawPublicKey))

    }

  }

}
