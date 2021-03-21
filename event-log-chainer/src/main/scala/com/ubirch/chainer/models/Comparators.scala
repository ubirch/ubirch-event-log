package com.ubirch.chainer.models

object Comparators {

  implicit def stringComparator(a: String, b: String): Boolean = a == b

}
