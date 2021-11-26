package com.ubirch.chainer.models

import com.ubirch.crypto.utils.{ Utils => UbirchUtils }

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.{ Charset, StandardCharsets }
import java.security.MessageDigest

object Hash {

  def algorithm = "SHA-512"

  sealed trait HashData[T] {
    def rawValue: T
  }

  case class BytesData(value: Array[Byte]) extends HashData[Array[Byte]] {
    def toHexStringData: HexStringData = HexStringData(Hex.toHexString(value))
    override def rawValue: Array[Byte] = value
  }
  case class HexStringData(value: String) extends HashData[String] {
    def toBytesData: BytesData = BytesData(Hex.decode(value))
    override def rawValue: String = value
  }
  case class StringData(value: String, charset: Charset = StandardCharsets.UTF_8) extends HashData[String] {
    def toBytesData: BytesData = BytesData(value.getBytes(charset))
    override def rawValue: String = value
  }

  def apply[T](v: HashData[T]): BytesData = {

    def getHash(data: BytesData): BytesData = {
      val messageDigest = MessageDigest.getInstance(algorithm, UbirchUtils.SECURITY_PROVIDER_NAME)
      messageDigest.update(data.value)
      BytesData(messageDigest.digest())
    }

    v match {
      case d @ BytesData(_) => getHash(d)
      case d @ HexStringData(_) => getHash(d.toBytesData)
      case d @ StringData(_, _) => getHash(d.toBytesData)
    }
  }

  def apply(v1: BytesData, v2: BytesData): BytesData = apply(BytesData(Array.concat(v1.value, v2.value)))
  def apply(v1: HexStringData, v2: HexStringData): BytesData = apply(v1.toBytesData, v2.toBytesData)
  def apply(v1: StringData, v2: StringData): BytesData = apply(v1.toBytesData, v2.toBytesData)

}
