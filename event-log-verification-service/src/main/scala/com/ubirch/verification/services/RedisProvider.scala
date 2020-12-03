package com.ubirch.verification.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._

import scala.concurrent.Future

@Singleton
class RedisProvider @Inject() (config: Config, lifecycle: Lifecycle) extends Provider[RedisCache] with StrictLogging {

  private val redis: RedisCache = new RedisCache("verification", config)

  override def get(): RedisCache = redis

  lifecycle.addStopHook { () =>

    logger.info("Shutting down Redis Client")

    Future.successful {
      if (redis != null) {
        if (redis.redisson != null) {
          redis.redisson.shutdown()
        }
      }
    }

  }

}

