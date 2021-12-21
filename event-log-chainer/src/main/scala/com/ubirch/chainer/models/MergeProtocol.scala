package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ BytesData, HexStringData }

/**
  * Represents a function for merging two values of type H
  * @tparam H Represents the type H that will be merged
  */
trait MergeProtocol[H] extends ((H, H) => H) {

  /**
    * Represents the version of the protocol
    * @return version as int
    */
  def version: Int

  /**
    * Represents a function that compares two values of type H
    * @param a initial value of type H
    * @param b second value of type H
    * @return Boolean for the comparison
    */
  def equals(a: H, b: H): Boolean

}

/**
  * Companion object of merge protocols
  */
object MergeProtocol {

  /**
    * Protocol that understands hex string directly
    * @return MergeProtocol of HexStrings
    */
  def V2_HexString: MergeProtocol[String] = new MergeProtocol[String] {
    override def version: Int = (2 << 4) | 0x01
    override def equals(a: String, b: String): Boolean = a == b
    override def apply(a: String, b: String): String = Hash(HexStringData(a), HexStringData(b)).toHexStringData.rawValue
  }

  /**
    * Protocol that understands bytes directly
    * @return MergeProtocol of bytes
    */
  def V2_Bytes: MergeProtocol[Array[Byte]] = new MergeProtocol[Array[Byte]] {
    override def version: Int = (2 << 4) | 0x02
    override def equals(a: Array[Byte], b: Array[Byte]): Boolean = a sameElements b
    override def apply(a: Array[Byte], b: Array[Byte]): Array[Byte] = Hash(BytesData(a), BytesData(b)).rawValue
  }
}
