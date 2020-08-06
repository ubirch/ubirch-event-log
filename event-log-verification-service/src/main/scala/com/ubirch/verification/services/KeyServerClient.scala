package com.ubirch.verification.services

import com.typesafe.config.Config
import com.ubirch.client.keyservice.UbirchKeyService
import javax.inject._

@Singleton
class KeyServerClient @Inject() (config: Config) extends UbirchKeyService({
  val url = config.getString("ubirchKeyService.client.rest.host")
  if (url.startsWith("http://") || url.startsWith("https://")) url else s"http://$url"
})
