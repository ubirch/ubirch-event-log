package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ HexStringData, StringData }
import com.ubirch.util.UUIDHelper

import scala.util.Try

trait BalancingProtocol[H] extends (List[H] => Option[H]) {
  def version: Int
}

object BalancingProtocol {

  def getEmptyNode: HexStringData = Hash(StringData(s"emptyNode_${UUIDHelper.randomUUID}")).toHexStringData

  def RandomHexString(maybeInitHash: Option[String] = None): BalancingProtocol[String] =
    new BalancingProtocol[String] {
      override def version: Int = (2 << 4) | 0x01
      override def apply(v1: List[String]): Option[String] =
        maybeInitHash.orElse(Some(getEmptyNode.rawValue))
    }

  def RandomBytes: BalancingProtocol[Array[Byte]] =
    new BalancingProtocol[Array[Byte]] {
      override def version: Int = (2 << 4) | 0x02
      override def apply(v1: List[Array[Byte]]): Option[Array[Byte]] =
        Option(getEmptyNode.toBytesData.rawValue)
    }

  def LastHexString: BalancingProtocol[String] =
    new BalancingProtocol[String] {
      override def version: Int = (2 << 4) | 0x03
      override def apply(v1: List[String]): Option[String] =
        Try(v1.last).toOption
    }

}
