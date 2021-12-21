package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ HexStringData, StringData }
import com.ubirch.util.UUIDHelper

import scala.util.Try

/**
  * Represents a function that knows how to generate a new value H for balancing purposes.
  * @tparam H type for the hashable value
  */
trait BalancingProtocol[H] extends (List[H] => Option[H]) {
  /**
    * Represents the balancing protocol version
    * @return version of the protocol
    */
  def version: Int
}

/**
  * Companion object that contains balancing protocols
  */
object BalancingProtocol {

  /**
    * Random HexString
    * @return HexStringData
    */
  def getRandomValue: HexStringData = Hash(StringData(s"emptyNode_${UUIDHelper.randomUUID}")).toHexStringData

  /**
    * Function that creates random HexStrings
    * @param maybeInitHash possible value to use instead of generating one.
    * @return Random HexString BalancingProtocol
    */
  def RandomHexString(maybeInitHash: Option[String] = None): BalancingProtocol[String] =
    new BalancingProtocol[String] {
      override def version: Int = (2 << 4) | 0x01
      override def apply(v1: List[String]): Option[String] =
        maybeInitHash.orElse(Some(getRandomValue.rawValue))
    }

  /**
    * Function that creates random bytes
    * @return Random Bytes BalancingProtocol
    */
  def RandomBytes: BalancingProtocol[Array[Byte]] =
    new BalancingProtocol[Array[Byte]] {
      override def version: Int = (2 << 4) | 0x02
      override def apply(v1: List[Array[Byte]]): Option[Array[Byte]] =
        Option(getRandomValue.toBytesData.rawValue)
    }

  /**
    * Function that takes the last value in the incoming list
    * @return Last Hex as BalancingProtocol
    */
  def LastHexString: BalancingProtocol[String] =
    new BalancingProtocol[String] {
      override def version: Int = (2 << 4) | 0x03
      override def apply(v1: List[String]): Option[String] =
        Try(v1.last).toOption
    }

}
