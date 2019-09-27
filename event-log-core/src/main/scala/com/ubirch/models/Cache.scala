package com.ubirch.models

import java.util.concurrent.ConcurrentHashMap

import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait Cache {
  def get[T](key: String): Future[Option[T]]
  def put[T](key: String, value: T): Future[Option[T]]
  def remove[T](key: String): Future[Option[T]]
  def remove[T](key: String, value: T): Future[Boolean]
}

@Singleton
class MemCache extends Cache {

  private final val map = new ConcurrentHashMap[String, Any]().asScala

  override def get[T](key: String): Future[Option[T]] = {
    Future.successful(map.get(key).map(_.asInstanceOf[T]))
  }

  override def put[T](key: String, value: T): Future[Option[T]] = {
    Future.successful(map.put(key, value).map(_.asInstanceOf[T]))
  }

  override def remove[T](key: String): Future[Option[T]] = {
    Future.successful(map.remove(key).map(_.asInstanceOf[T]))
  }

  override def remove[T](key: String, value: T): Future[Boolean] = {
    Future.successful(map.remove(key, value))
  }

}

object MemCache {
  final val name = "mem-cache"
}
