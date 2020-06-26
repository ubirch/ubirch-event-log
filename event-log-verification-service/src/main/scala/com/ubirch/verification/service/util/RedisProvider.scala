package com.ubirch.verification.service.util

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import javax.inject._

@Singleton
class RedisProvider @Inject()(config: Config) extends Provider[RedisOpt] with StrictLogging {


  private val redis: RedisOpt = {

    val result =
      try {
        Some(new RedisCache("verification", config))
      } catch {
        case ex: Throwable => logger.error("redis exception: ", ex)
          None
      }
    new RedisOpt(result)
  }

  override def get(): RedisOpt = redis

}

