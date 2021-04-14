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
      logger.warn("invalid_key=" + e.getMessage, e)
      false

    case e: NoSuchAlgorithmException =>
      logger.warn("algo_not_found=" + e.getMessage, e)
      false

    case e: Exception =>
      logger.error("error_getting_key=" + e.getMessage, e)
      false
  }
}
