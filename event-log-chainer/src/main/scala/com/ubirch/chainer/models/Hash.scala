package com.ubirch.chainer.models

import com.ubirch.crypto.utils.{ Utils => UbirchUtils }

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.{ Charset, StandardCharsets }
import java.security.MessageDigest

/**
  * Convenience for hashing values
  */
object Hash {

  /**
    * Represents the hashing algorithm to use.
    * We use SHA-512
    * @return algorithm as string
    */
  def algorithm = "SHA-512"

  /**
    * Wrapper of data T.
    * This is useful to recognize formats of types T before hashing
    * @tparam T Represents the raw value to be wrapped up
    */
  sealed trait HashData[T] {
    def rawValue: T
  }

  /**
    * Represents a data value for bytes
    * It provides helper function to encode bytes into HexString
    * @param value array of bytes
    */
  case class BytesData(value: Array[Byte]) extends HashData[Array[Byte]] {
    def toHexStringData: HexStringData = HexStringData(Hex.toHexString(value))
    override def rawValue: Array[Byte] = value
  }

  /**
    * Represents a data value for hex strings
    * It provides helper function to decode hex strings into bytes
    * @param value hex strings
    */
  case class HexStringData(value: String) extends HashData[String] {
    def toBytesData: BytesData = BytesData(Hex.decode(value))
    override def rawValue: String = value
  }

  /**
    * Represents a data value for simple strings
    * @param value strings of data
    * @param charset format for the charset
    */
  case class StringData(value: String, charset: Charset = StandardCharsets.UTF_8) extends HashData[String] {
    def toBytesData: BytesData = BytesData(value.getBytes(charset))
    override def rawValue: String = value
  }

  /**
    * Function that knows how to hash values of HashData of type T
    * @param v the wrapped hash data type
    * @tparam T the type in which the wrapped value is in
    * @return
    */
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

  /**
    * Function that knows how to hash two values of type BytesData
    * Note that the two values are concatenated as arrays of bytes
    * @param v1 left value for the hashing process
    * @param v2 right value for the hashing process
    * @return BytesData of the two ByteData types
    */
  def apply(v1: BytesData, v2: BytesData): BytesData = apply(BytesData(Array.concat(v1.value, v2.value)))

  /**
    * Function that knows how to hash two values of type HexStringData
    * Note that the two values transformed into byte data before merging.
    * @param v1 left value for the hashing process
    * @param v2 right value for the hashing process
    * @return ByteData of the two HexStringData types
    */
  def apply(v1: HexStringData, v2: HexStringData): BytesData = apply(v1.toBytesData, v2.toBytesData)

  /**
    * Function that knows how to hash two values of type StringData
    * Note that the two values transformed into byte data before merging
    * @param v1 left value for the hashing process
    * @param v2 right value for the hashing process
    * @return ByteData of the two StringData types
    */
  def apply(v1: StringData, v2: StringData): BytesData = apply(v1.toBytesData, v2.toBytesData)

}
