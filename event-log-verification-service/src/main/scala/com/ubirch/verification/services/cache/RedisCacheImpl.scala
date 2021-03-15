package com.ubirch.verification.services.cache

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.verification.ConfPaths.RedisConfPaths._
import scredis.Redis
import scredis.exceptions.RedisIOException
import scredis.protocol.AuthConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class RedisCacheImpl @Inject()(config: Config, lifecycle: Lifecycle) extends RedisCacheBase with StrictLogging {

  private val host = config.getString(REDIS_MAIN_HOST)
  private val port = config.getInt(REDIS_PORT)
  private val index = config.getInt(REDIS_INDEX)
  private val cacheTTL = config.getInt(REDIS_CACHE_TTL)

  private val password: String = config.getString(REDIS_PASSWORD)
  private val authOpt =
    if (password == "") None
    else Some(AuthConfig(None, password))

  /**
    * This redis instance doesn't connect yet. On the first access
    * a connection will be tried to become established. After that
    * the instance keeps the connection open until a certain timeout
    * is reached. (Haven't found the exact number yet.) For further
    * configurations look here:
    * https://github.com/scredis/scredis/blob/master/src/main/resources/reference.conf
    */
  private val redis: Redis = Redis(host, port, authOpt, index)

  /**
    * Checks if the hash/payload already is stored in the cache.
    *
    * @param hash key
    * @return optional value
    */
  @throws[RedisIOException]
  def get(hash: Array[Byte]): Future[Option[String]] = {
    redis.get(new String(hash))
  }

  /**
    * Sets a new key value pair to the cache or overwrites an
    * old one.
    *
    * @param hash key
    * @param upp  value
    */
  @throws[RedisIOException]
  def set(hash: Array[Byte], upp: String): Future[Unit] = {
    redis.setEX(new String(hash), upp, cacheTTL * 60)
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Redis if filter service.")
    Future.successful(redis.quit())
  }

}
