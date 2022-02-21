package com.ubirch.verification

import com.ubirch.models.{ EventLog, EventLogRow }
import com.ubirch.verification.models._
import com.ubirch.verification.services._
import com.ubirch.verification.services.eventlog.DefaultEventLogClient
import com.ubirch.verification.util.LookupJsonSupport

import org.json4s.JsonAST.JString
import org.scalatest._

import java.util.Base64
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.ExecutionContext.Implicits.global

class DefaultEventLogSpec extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  it should "Iota: check that versions are added 1.0.0" in {

    val finder = new Finder {
      override implicit def ec: ExecutionContext = global

      override def findEventLog(value: String, category: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")).withCategory(category))))

      override def findByPayload(value: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")))))

      override def findBySignature(value: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")))))

      override def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] =
        Future.successful((
          List(VertexStruct(
          id = "this is an id 1",
          label = "this is a label 1",
          properties = Map("a1" -> "b1")
        )),
          List(
            VertexStruct(
              "81948598504",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:42:53.198Z",
                "hash" -> "0xb225b88c5a402cdafae52c0b3aac1306fb76d55cad412db7b0390d9dd9995790",
                "public_chain" -> "ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK",
                "prev_hash" -> "42cda58bed2ff39b7db5340dc5be8e93e13ab09686127fa60a2290ff968b14484eada600e850634ce945bdb0a16c6a5bc8f5a05d1da76ed45b0d79a6ea1d55b5"
              )
            ),

            VertexStruct(
              "81976901656",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:41:45.784Z",
                "hash" -> "DERWCJATHUNXBUZUKEOAFVFRHWAQDRWTLBBXU9IVVTKBCTKHHLEJGMNKVY99HGVJSW9SQLNU9JWMA9999",
                "public_chain" -> "IOTA_MAINNET_IOTA_MAINNET_NETWORK",
                "prev_hash" -> "eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"
              )
            ),

            VertexStruct(
              "204856877224",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:41:57.210Z",
                "hash" -> "0x51789b2341d9219c1d3893ec412dc1a8c5b7ec5526ccfed1203ae3585a7943b5",
                "public_chain" -> "GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK",
                "prev_hash" -> "eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"
              )
            )

          )
        ))

      override def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
        Future.successful((Nil, Nil, Nil, Nil))
    }

    val client = new DefaultEventLogClient(finder)
    val res = client.getEventByHash(
      hash = Base64.getDecoder.decode("nPw6H0YLymozFqY4qlOQAAe18vBm6g4FhihFZcA5/tg="),
      queryDepth = ShortestPath,
      responseForm = AnchorsNoPath,
      blockchainInfo = Extended
    )

    val lookupRes = Await.result(res, 10.seconds)

    assert(LookupJsonSupport.ToJson[LookupResult](lookupRes).toString == """{"success":true,"value":"nPw6H0YLymozFqY4qlOQAAe18vBm6g4FhihFZcA5/tg=","query_type":"payload","message":"Query Successfully Processed","event":"hello","anchors":[{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:42:53.198Z","hash":"0xb225b88c5a402cdafae52c0b3aac1306fb76d55cad412db7b0390d9dd9995790","public_chain":"ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK","prev_hash":"42cda58bed2ff39b7db5340dc5be8e93e13ab09686127fa60a2290ff968b14484eada600e850634ce945bdb0a16c6a5bc8f5a05d1da76ed45b0d79a6ea1d55b5"}},{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:41:45.784Z","version":"1.0.0","hash":"DERWCJATHUNXBUZUKEOAFVFRHWAQDRWTLBBXU9IVVTKBCTKHHLEJGMNKVY99HGVJSW9SQLNU9JWMA9999","public_chain":"IOTA_MAINNET_IOTA_MAINNET_NETWORK","prev_hash":"eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"}},{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:41:57.210Z","hash":"0x51789b2341d9219c1d3893ec412dc1a8c5b7ec5526ccfed1203ae3585a7943b5","public_chain":"GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK","prev_hash":"eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"}}]}""".stripMargin)
  }

  it should "Iota: check that versions are added 1.5.0" in {

    val finder = new Finder {
      override implicit def ec: ExecutionContext = global

      override def findEventLog(value: String, category: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")).withCategory(category))))

      override def findByPayload(value: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")))))

      override def findBySignature(value: String): Future[Option[EventLogRow]] =
        Future.successful(Option(EventLogRow.fromEventLog(EventLog(JString("hello")))))

      override def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] =
        Future.successful((
          List(VertexStruct(
          id = "this is an id 1",
          label = "this is a label 1",
          properties = Map("a1" -> "b1")
        )),
          List(
            VertexStruct(
              "81948598504",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:42:53.198Z",
                "hash" -> "0xb225b88c5a402cdafae52c0b3aac1306fb76d55cad412db7b0390d9dd9995790",
                "public_chain" -> "ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK",
                "prev_hash" -> "42cda58bed2ff39b7db5340dc5be8e93e13ab09686127fa60a2290ff968b14484eada600e850634ce945bdb0a16c6a5bc8f5a05d1da76ed45b0d79a6ea1d55b5"
              )
            ),

            VertexStruct(
              "81976901656",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:41:45.784Z",
                "hash" -> "1ae5eaaad6efc1d6ef2d687524a9128128ae63fa8c943ae91649f02838683f1f",
                "public_chain" -> "IOTA_MAINNET_IOTA_MAINNET_NETWORK",
                "prev_hash" -> "eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"
              )
            ),

            VertexStruct(
              "204856877224",
              "PUBLIC_CHAIN", Map(
                "timestamp" -> "2021-02-28T23:41:57.210Z",
                "hash" -> "0x51789b2341d9219c1d3893ec412dc1a8c5b7ec5526ccfed1203ae3585a7943b5",
                "public_chain" -> "GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK",
                "prev_hash" -> "eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"
              )
            )

          )
        ))

      override def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
        Future.successful((Nil, Nil, Nil, Nil))
    }

    val client = new DefaultEventLogClient(finder)
    val res = client.getEventByHash(
      hash = Base64.getDecoder.decode("nPw6H0YLymozFqY4qlOQAAe18vBm6g4FhihFZcA5/tg="),
      queryDepth = ShortestPath,
      responseForm = AnchorsNoPath,
      blockchainInfo = Extended
    )

    val lookupRes = Await.result(res, 10.seconds)

    assert(LookupJsonSupport.ToJson[LookupResult](lookupRes).toString == """{"success":true,"value":"nPw6H0YLymozFqY4qlOQAAe18vBm6g4FhihFZcA5/tg=","query_type":"payload","message":"Query Successfully Processed","event":"hello","anchors":[{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:42:53.198Z","hash":"0xb225b88c5a402cdafae52c0b3aac1306fb76d55cad412db7b0390d9dd9995790","public_chain":"ETHEREUM-CLASSIC_TESTNET_ETHERERUM_CLASSIC_KOTTI_TESTNET_NETWORK","prev_hash":"42cda58bed2ff39b7db5340dc5be8e93e13ab09686127fa60a2290ff968b14484eada600e850634ce945bdb0a16c6a5bc8f5a05d1da76ed45b0d79a6ea1d55b5"}},{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:41:45.784Z","version":"1.5.0","hash":"1ae5eaaad6efc1d6ef2d687524a9128128ae63fa8c943ae91649f02838683f1f","public_chain":"IOTA_MAINNET_IOTA_MAINNET_NETWORK","prev_hash":"eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"}},{"label":"PUBLIC_CHAIN","properties":{"timestamp":"2021-02-28T23:41:57.210Z","hash":"0x51789b2341d9219c1d3893ec412dc1a8c5b7ec5526ccfed1203ae3585a7943b5","public_chain":"GOV-DIGITAL_MAINNET_GOV_DIGITAL_MAINNET_NETWORK","prev_hash":"eb5d8daa5cf60c976bb8e2747f23336fd70a4562701a9fd9f61deae833546df414b774ae4c18b67001e1f036efb4752f8b7332936caab7b07a8dd3dc70cbc5d4"}}]}""".stripMargin)
  }

}

