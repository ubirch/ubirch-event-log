package com.ubirch.chainer.models

import com.ubirch.TestBase

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.StandardCharsets

class BalancingProtocolSpec extends TestBase {

  "A RandomHexString(maybeInitHash: Option[String] = None)" must {

    "have expected values" in {

      val initHash = Some(Hex.toHexString(getHash("Hola".getBytes(StandardCharsets.UTF_8))))
      val bp = BalancingProtocol.RandomHexString(initHash)
      assert(initHash == bp(Nil))
      assert(bp.version == ((2 << 4) | 0x01))

      val bp1 = BalancingProtocol.RandomHexString(None)
      assert(bp1(List("")).isDefined)

    }

  }

  "A RandomBytes: BalancingProtocol[Array[Byte]]" must {

    "have expected values" in {

      val bp = BalancingProtocol.RandomBytes
      assert(bp(Nil).map(x => Hex.toHexString(x)).isDefined)
      assert(bp.version == ((2 << 4) | 0x02))

    }

  }

  "A LastHexString: BalancingProtocol[String]" must {

    "have expected values" in {

      val bp = BalancingProtocol.LastHexString
      val a = Hex.toHexString(getHash("a".getBytes(StandardCharsets.UTF_8)))
      val b = Hex.toHexString(getHash("b".getBytes(StandardCharsets.UTF_8)))

      assert(bp(List(a, b)) == Option(b))
      assert(bp.version == ((2 << 4) | 0x03))

    }

  }

}
