package com.ubirch.chainer.services.tree

import com.typesafe.scalalogging.LazyLogging
import javax.inject._

sealed trait WarmUpResult

case object AllGood extends WarmUpResult
case object WhatTheHeck extends WarmUpResult
case object CreateGenesisTree extends WarmUpResult

@Singleton
class TreeWarmUp @Inject()(treeCache: TreeCache) extends LazyLogging {

  def warmup: WarmUpResult = {
    logger.info("Starting Tree Warm-up...")
    val mfe = firstEver.filter(_.nonEmpty)
    val mlt = lastest.filter(_.nonEmpty)

    (mfe, mlt) match {
      case (Some(fe), None) =>
        logger.info("Genesis Tree and No Latest Tree found. Setting local as Genesis as [{}]", fe)
        treeCache.setLatestHash(fe)
        AllGood
      case (Some(_), Some(lt)) =>
        logger.info("Genesis Tree and Latest Tree found. Setting local Latest as [{}]", lt)
        treeCache.setLatestHash(lt)
        AllGood
      case (None, Some(lt)) =>
        logger.error("WHAT: There's a latest tree BUT no genesis! ")
        WhatTheHeck
      case (None, None) =>
        logger.info("Nothing found. This is the beginning of the universe. Creating first Tree Ever")
        CreateGenesisTree

    }

  }
  def firstEver: Option[String] = {
    logger.info("Checking Genesis Tree ...")
    None
  }
  def lastest : Option[String] = {
    logger.info("Checking Latest Tree ...")
    Some("hooa")
  }
}
