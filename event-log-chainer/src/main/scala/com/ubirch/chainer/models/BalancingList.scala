package com.ubirch.chainer.models

/**
  * Represents a simple helper for lists
  */
object BalancingList {

  private def balance[B](balanceValue: => B)(f: (List[B], List[B]) => List[B])(values: List[B]): List[B] = {
    if (values.size % 2 == 0) {
      values
    } else {
      f(values, List(balanceValue))
    }
  }

  def balanceRight[B](balancingValue: => B)(values: List[B]): List[B] = {
    balance(balancingValue)((v, b) => v ++ b)(values)
  }

  def balanceLeft[B](balanceWithF: => B)(values: List[B]): List[B] = {
    balance(balanceWithF)((v, b) => b ++ v)(values)
  }

}
