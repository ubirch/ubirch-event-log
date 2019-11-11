package com.ubirch.service.rest

import com.typesafe.config.Config
import javax.inject.Inject

class ServerConfig @Inject() (config: Config) {

  val serverPort: Int = config.getInt("eventLog.server.port")
  val serverBaseUrl: String = config.getString("eventLog.server.baseUrl")
  val appVersion: String = config.getString("eventLog.version")
  val swaggerPath: String = config.getString("eventLog.server.swaggerPath")
  val contextPathBase: String = serverBaseUrl + "/" + appVersion

  def createURL(path: String): String = {
    val cxt = if (serverBaseUrl.isEmpty) "http://localhost:" + serverPort + contextPathBase
    else contextPathBase
    cxt + path.replaceAll("//", "/")
  }

}
