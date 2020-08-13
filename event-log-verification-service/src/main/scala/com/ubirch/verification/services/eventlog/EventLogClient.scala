package com.ubirch.verification.services.eventlog

import com.ubirch.verification.models.{ BlockchainInfo, LookupResult, QueryDepth, ResponseForm }

import scala.concurrent.Future

trait EventLogClient {

  def getEventByHash(
      hash: Array[Byte],
      queryDepth: QueryDepth,
      responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo
  ): Future[LookupResult]

  def getEventBySignature(
      signature: Array[Byte],
      queryDepth: QueryDepth,
      responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo
  ): Future[LookupResult]

}
