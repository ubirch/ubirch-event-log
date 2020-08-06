package com.ubirch.verification.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import javax.inject._

@Singleton
class RedisProvider @Inject() (config: Config) extends Provider[RedisCache] with StrictLogging {

  private val redis: RedisCache = new RedisCache("verification", config)

  override def get(): RedisCache = redis

}

