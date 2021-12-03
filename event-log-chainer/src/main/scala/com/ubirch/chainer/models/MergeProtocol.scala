package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ BytesData, HexStringData }

trait MergeProtocol[H] {
  def version: String
  def merger: (H, H) => H
}

object MergeProtocol {
  def V2_HexString: MergeProtocol[String] = new MergeProtocol[String] {
    override def version: String = "v2"
    override def merger: (String, String) => String =
      (a, b) => Hash(HexStringData(a), HexStringData(b)).toHexStringData.rawValue
  }
  def V2_Bytes: MergeProtocol[Array[Byte]] = new MergeProtocol[Array[Byte]] {
    override def version: String = "v2"
    override def merger: (Array[Byte], Array[Byte]) => Array[Byte] =
      (a, b) => Hash(BytesData(a), BytesData(b)).rawValue
  }
}
