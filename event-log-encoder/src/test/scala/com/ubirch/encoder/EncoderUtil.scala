package com.ubirch.encoder

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64

import java.security.{ MessageDigest, Security }

object EncoderUtil {
  def getDigest(data: Array[Byte]) = {
    Security.addProvider(new BouncyCastleProvider())
    val digest = MessageDigest.getInstance("SHA-256", "BC")
    digest.update(data)
    Base64.toBase64String(digest.digest)
  }
}
