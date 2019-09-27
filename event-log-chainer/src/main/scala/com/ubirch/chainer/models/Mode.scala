package com.ubirch.chainer.models

import com.ubirch.models.Values

sealed trait Mode {
  val value: String
  val category: String
  val serviceClass: String
  val lookupName: String
  val customerId: String
}

object Mode {
  def getMode(mode: String): Mode = {
    mode.toUpperCase match {
      case Slave.value => Slave
      case Master.value => Master
    }
  }

  def fold[R](mode: Mode)(onSlave: () => R)(onMaster: () => R): R = mode match {
    case Slave => onSlave()
    case Master => onMaster()
  }

  def foldF[R](mode: Mode): Fold[R] = Fold[R](mode, None, None)

  case class Fold[R](mode: Mode, onSlave: Option[() => R] = None, onMaster: Option[() => R] = None) {
    def onSlave(newOnSlave: () => R): Fold[R] = copy(onSlave = Some(newOnSlave))
    def onMaster(newOnMaster: () => R): Fold[R] = copy(onMaster = Some(newOnMaster))
    def run: R = fold(mode)(onSlave.getOrElse(throw new IllegalArgumentException("No onSlave has been set.")))(onMaster.getOrElse(throw new IllegalArgumentException("No onMaster has been set.")))
  }

}

case object Slave extends Mode {
  override val value: String = "SLAVE"
  override val category: String = Values.SLAVE_TREE_CATEGORY
  override val serviceClass: String = "ubirchChainerSlave"
  override val lookupName: String = Values.SLAVE_TREE_ID
  override val customerId: String = Values.UBIRCH
}

case object Master extends Mode {
  override val value: String = "MASTER"
  override val category: String = Values.MASTER_TREE_CATEGORY
  override val serviceClass: String = "ubirchChainerMaster"
  override val lookupName: String = Values.MASTER_TREE_ID
  override val customerId: String = Values.UBIRCH
}
