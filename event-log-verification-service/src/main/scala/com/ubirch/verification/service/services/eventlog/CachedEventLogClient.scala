package com.ubirch.verification.service.services.eventlog

import com.ubirch.niomon.cache.RedisCache
import com.ubirch.verification.service.models.{BlockchainInfo, QueryDepth, ResponseForm}
import javax.inject.{Inject, Named}

import scala.concurrent.{ExecutionContext, Future}

class CachedEventLogClient @Inject()(@Named("New") underlying: EventLogClient, redisCache: RedisCache)(implicit ec: ExecutionContext) extends EventLogClient {

  private val getEventByHashCached =
    redisCache
      .cachedF(underlying.getEventByHash _)
      .buildCache("event-by-hash-cache", r => r.success)({ key =>
        val (hash, qd, rf, bi) = key
        s"h=[${hash.mkString(",")}]$qd$rf$bi"
      }, ec)

  private val getEventBySignatureCached =
    redisCache
      .cachedF(underlying.getEventBySignature _)
      .buildCache("event-by-signature-cache", r => r.success)({ key =>
        val (sig, qd, rf, bi) = key
        s"s=[${sig.mkString(",")}]$qd$rf$bi"
      }, ec)

  override def getEventByHash(hash: Array[Byte],
                              queryDepth: QueryDepth,
                              responseForm: ResponseForm,
                              blockchainInfo: BlockchainInfo
                             ): Future[EventLogClient.Response] =

    getEventByHashCached(hash, queryDepth, responseForm, blockchainInfo)

  override def getEventBySignature(signature: Array[Byte],
                                   queryDepth: QueryDepth,
                                   responseForm: ResponseForm,
                                   blockchainInfo: BlockchainInfo
                                  ): Future[EventLogClient.Response] =

    getEventBySignatureCached(signature, queryDepth, responseForm, blockchainInfo)
}
