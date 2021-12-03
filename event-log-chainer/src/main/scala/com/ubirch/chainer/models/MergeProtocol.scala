package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ BytesData, HexStringData }

trait MergeProtocol[H] {
  def version: Int

  def equals(a: H, b: H): Boolean

  def merger: (H, H) => H
}

object MergeProtocol {
  def V2_HexString: MergeProtocol[String] = new MergeProtocol[String] {
    override def version: Int = (2 << 4) | 0x01
    override def equals(a: String, b: String): Boolean = a == b
    override def merger: (String, String) => String =
      (a, b) => Hash(HexStringData(a), HexStringData(b)).toHexStringData.rawValue
  }
  def V2_Bytes: MergeProtocol[Array[Byte]] = new MergeProtocol[Array[Byte]] {
    override def version: Int = (2 << 4) | 0x02
    override def equals(a: Array[Byte], b: Array[Byte]): Boolean = a sameElements b
    override def merger: (Array[Byte], Array[Byte]) => Array[Byte] =
      (a, b) => Hash(BytesData(a), BytesData(b)).rawValue
  }
}
