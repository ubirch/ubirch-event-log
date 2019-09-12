package com.ubirch.chainer.services

import com.ubirch.models.{ Cache, MemCache }
import javax.inject._

import scala.concurrent.ExecutionContext

@Singleton
class TreeCache @Inject() (@Named(MemCache.name) cache: Cache)(implicit ec: ExecutionContext) {

  val LATEST_SLAVE_TREE = "LASTEST_SLAVE_TREE"

  def latest = cache.get(LATEST_SLAVE_TREE)

  def setLatest(value: String) = cache.put(LATEST_SLAVE_TREE, value)

}
