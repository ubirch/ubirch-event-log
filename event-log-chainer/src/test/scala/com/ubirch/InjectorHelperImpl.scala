package com.ubirch

import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.models._
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.chainer.services.httpClient.{ WebClient, WebclientResponse }
import com.ubirch.chainer.services.tree.TreePaths
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._

import com.google.inject.Provider
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import org.asynchttpclient.Param

import java.io.ByteArrayInputStream
import java.util.concurrent.Executor
import scala.concurrent.Future

class WebClientProvider extends Provider[WebClient] {
  override def get(): WebClient = new WebClient {
    override def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse] = {
      url match {
        case "http://localhost:8081/v1/events" =>
          val body = """{"success":true,"message":"Nothing Found","data":[]}"""
          Future.successful(WebclientResponse("OK", 200, "application/json", body, new ByteArrayInputStream(body.getBytes())))
      }
    }
  }
}

class InjectorHelperImpl(
    bootstrapServers: String,
    consumerTopic: String,
    producerTopic: String,
    minTreeRecords: Int = 10,
    treeEvery: Int = 60,
    treeUpgrade: Int = 120,
    mode: Mode = Slave,
    split: Boolean = false,
    splitSize: Int = 50,
    webClientProvider: Option[Provider[WebClient]] = None
) extends InjectorHelper(List(new ChainerServiceBinder {

  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(TreePaths.DAYS_BACK, ConfigValueFactory.fromAnyRef(3))
        .withValue(TreePaths.SPLIT, ConfigValueFactory.fromAnyRef(split))
        .withValue(TreePaths.SPLIT_SIZE, ConfigValueFactory.fromAnyRef(splitSize))
        .withValue(TreePaths.MODE, ConfigValueFactory.fromAnyRef(mode.value))
        .withValue(TreePaths.MIN_TREE_RECORDS, ConfigValueFactory.fromAnyRef(minTreeRecords))
        .withValue(TreePaths.TREE_EVERY, ConfigValueFactory.fromAnyRef(treeEvery))
        .withValue(TreePaths.TREE_UPGRADE, ConfigValueFactory.fromAnyRef(treeUpgrade))
        .withValue(ConsumerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
        .withValue(ProducerConfPaths.BOOTSTRAP_SERVERS, ConfigValueFactory.fromAnyRef(bootstrapServers))
        .withValue(ConsumerConfPaths.TOPIC_PATH, ConfigValueFactory.fromAnyRef(consumerTopic))
        .withValue(ProducerConfPaths.TOPIC_PATH, ConfigValueFactory.fromAnyRef(producerTopic))
    }
  })

  override def webClient: ScopedBindingBuilder = webClientProvider.map(x => bind(classOf[WebClient]).toProvider(x)).getOrElse(super.webClient)
}))

