package com.ubirch.verification

object PortGiver {

  private var healthCheckPort = 8888

  def giveMeHealthCheckPort: Int = {
    this.synchronized {
      healthCheckPort = healthCheckPort + 1
      healthCheckPort
    }
  }

}
