package com.ubirch.util

import java.net.InetSocketAddress

import com.datastax.driver.core.Cluster.Builder
import com.typesafe.config.ConfigFactory
import com.ubirch.TestBase
import io.getquill.context.cassandra.cluster.ClusterBuilder

class QuillBuilderSpec extends TestBase {

  val hosts = List("127.0.0.1", "127.0.0.2", "127.0.0.3")
  val contactPoints = hosts.map(new InetSocketAddress(_, 9042))

  "creates Builder" must {

    "with a single host" in {
      val cfgString = s"""contactPoint = ${hosts.head}"""
      val clusterBuilder: Builder = ClusterBuilder(ConfigFactory.parseString(cfgString))
      clusterBuilder.getContactPoints must contain theSameElementsAs contactPoints.take(1)
    }

    "with a single host in an array" in {
      val cfgString = s"""contactPoints = [${hosts.head}]"""
      val clusterBuilder: Builder = ClusterBuilder(ConfigFactory.parseString(cfgString))
      clusterBuilder.getContactPoints must contain theSameElementsAs contactPoints.take(1)

    }

    "with multiple hosts" in {
      val cfgString = s"""contactPoints = [${hosts.mkString(",")}] """
      val clusterBuilder: Builder = ClusterBuilder(ConfigFactory.parseString(cfgString))
      clusterBuilder.getContactPoints must contain theSameElementsAs contactPoints
    }
  }

}
