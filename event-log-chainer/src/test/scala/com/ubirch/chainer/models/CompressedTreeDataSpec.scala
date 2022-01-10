package com.ubirch.chainer.models

import com.ubirch.TestBase

class CompressedTreeDataSpec extends TestBase {

  "A CompressedTreeData" must {

    "have as version two parts" in {
      val cd = CompressedTreeData("33.32", "root", List("l1", "l2"))
      assert(cd.versions == List(33, 32))
      assert(cd.mergeProtocolVersion == 33)
      assert(cd.balancingProtocolVersion == 32)
    }

    "complain when no point found" in {
      def cd = CompressedTreeData("33", "root", List("l1", "l2"))
      assertThrows[IllegalArgumentException](cd)
    }

  }

}
