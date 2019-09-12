package com.ubirch.models

import java.util.concurrent.ConcurrentHashMap

import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait Cache {
  def get(key: String): Future[Option[String]]
  def put(key: String, value: String): Future[Option[String]]
}

@Singleton
class MemCache extends Cache {

  private final val map = new ConcurrentHashMap[String, String]().asScala

  override def get(key: String): Future[Option[String]] = Future.successful(map.get(key))

  override def put(key: String, value: String): Future[Option[String]] = Future.successful(map.put(key, value))

}

object MemCache {
  final val name = "mem-cache"
}
