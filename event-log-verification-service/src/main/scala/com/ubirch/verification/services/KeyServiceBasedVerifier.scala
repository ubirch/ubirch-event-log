package com.ubirch.verification.services

import java.security.{ InvalidKeyException, NoSuchAlgorithmException }

import com.ubirch.client.protocol.DefaultProtocolVerifier
import com.ubirch.protocol.ProtocolMessage
import javax.inject._

@Singleton
class KeyServiceBasedVerifier @Inject() (keyServer: KeyServerClient) extends DefaultProtocolVerifier(keyServer) {
  def verifySuppressExceptions(pm: ProtocolMessage): Boolean = try {
    verify(pm.getUUID, pm.getSigned, 0, pm.getSigned.length, pm.getSignature)
  } catch {
    case e: InvalidKeyException =>
      logger.warn("Invalid Key", e)
      false

    case e: NoSuchAlgorithmException =>
      logger.warn(e.getMessage)
      false

    case e: Exception =>
      logger.error("Unexpected error", e)
      false
  }
}
