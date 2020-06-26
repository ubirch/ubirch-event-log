package com.ubirch.verification.service.services.eventlog

import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.verification.service.models.{BlockchainInfo, QueryDepth, ResponseForm}
import javax.inject.{Inject, Named}

import scala.concurrent.{ExecutionContext, Future}

class CachedEventLogClient @Inject()(@Named("New") underlying: EventLogClient, redis: RedisCache)
                                    (implicit ec: ExecutionContext) extends EventLogClient with StrictLogging {

  private val getEventByHashCached =
    redis
      .cachedF(underlying.getEventByHash _)
      .buildCache("eventResponse-by-hash-cache", r => r.success)({ key =>
        val (hash, qd, rf, bi) = key
        s"h=[${hash.mkString(",")}]$qd$rf$bi"
      }, ec)

  private val getEventBySignatureCached =
    redis
      .cachedF(underlying.getEventBySignature _)
      .buildCache("eventResponse-by-signature-cache", r => r.success)({ key =>
        val (sig, qd, rf, bi) = key
        s"s=[${sig.mkString(",")}]$qd$rf$bi"
      }, ec)

  override def getEventByHash(hash: Array[Byte],
                              queryDepth: QueryDepth,
                              responseForm: ResponseForm,
                              blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {

    try {
      getEventByHashCached(hash, queryDepth, responseForm, blockchainInfo)
    } catch {
      case ex: Throwable =>
        logger.error("redis error; ", ex)
        underlying.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
    }
  }

  override def getEventBySignature(signature: Array[Byte],
                                   queryDepth: QueryDepth,
                                   responseForm: ResponseForm,
                                   blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =

    try {
      getEventBySignatureCached(signature, queryDepth, responseForm, blockchainInfo)
    } catch {
      case ex: Throwable =>
        logger.error("redis error; ", ex)
        underlying.getEventBySignature(signature, queryDepth, responseForm, blockchainInfo)
    }
}
