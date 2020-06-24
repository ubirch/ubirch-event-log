package com.ubirch.verification.service.util

import com.typesafe.config.Config
import com.ubirch.niomon.cache.RedisCache
import javax.inject._

@Singleton
class VerificationRedisProvider @Inject() (config: Config) extends Provider[RedisCache] {

  private val redis = new RedisCache("event-log-verification-service", config)

  override def get(): RedisCache = redis

}
