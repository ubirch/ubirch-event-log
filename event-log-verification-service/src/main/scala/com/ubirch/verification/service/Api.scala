package com.ubirch.verification.service

import com.avsystem.commons.rpc.AsRaw
import com.fasterxml.jackson.databind.JsonNode
import com.ubirch.verification.service.models.{ AnchorsNoPath, Normal }
import com.ubirch.verification.service.util.udash.{ VerificationServiceRestApiCompanion, cors }
import io.udash.rest.openapi.adjusters.{ adjustSchema, example }
import io.udash.rest.openapi.{ DataType, RefOr, RestSchema, Schema }
import io.udash.rest.raw.{ HttpBody, IMapping, JsonValue, RestResponse }
import io.udash.rest.{ Query, _ }

import scala.concurrent.Future
import scala.language.implicitConversions

trait Api {
  @cors
  @GET
  def health: Future[String]

  @cors
  @CustomBody
  @POST("upp")
  def getUPP(hash: Array[Byte], @Query disableRedisLookup: Boolean = false): Future[Api.Response]

  @cors
  @CustomBody // without that this api endpoint would expect json `{"payload": []}`
  @POST("upp/verify")
  def verifyUPP(hash: Array[Byte]): Future[Api.Response]

  @cors
  @CustomBody
  @POST("upp/verify/anchor")
  def verifyUPPWithUpperBound(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value
  ): Future[Api.Response]

  @cors
  @CustomBody
  @POST("upp/verify/record")
  def verifyUPPWithUpperAndLowerBound(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value
  ): Future[Api.Response]

}

object Api extends VerificationServiceRestApiCompanion[Api] {
  private val exampleAnchor = Anchors(JsonValue(
    """[
    {
      "status": "added",
      "txid": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
      "message": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
      "blockchain": "ethereum",
      "network_info": "Rinkeby Testnet Network",
      "network_type": "testnet",
      "created": "2019-05-07T21:30:14.421095"
    }
  ]"""
  ))

  @example(Success(
    "lhOw04RN6zcFSPajBZ4pk/07+toAQGT2VYpTD9sOIs+LNdLX6WpDIyV7LnKNWzaZks7bifVJ0lnRa+OK1+MM9vgKmk3ISXCLnfC4zKPxpgCcnfkV6g4A2gBA91KZCJdgs9D/6d5yyrYaSojB2ABwQYFMXYjyQJ4ilc9ZHlGx9tBxYIUfh4Zd3rp4+P1WWeb7GPiYjf9Q/FduR9oAQCq/NbkwJMzV5cZQzq09zPhxd/+QsquDR2kv5RxQdH1lr+IKe9jsJ6rZFsnw+P4GXJHg5s7/GJJxMtfgXEEQ1wY=",
    "lhOw04RN6zcFSPajBZ4pk/07+toAQNbxMa02n0X1UgdLsIO8g/nNsQjC0F74DfnAWsqrRbQ/xv/scpEB123uHbgrrVysrIJjVFCrlhV+YLpNLgTpaQwA2gBAEJnT4GutCruP4FebT4ozsKtMYvCdkBybBdHNbd6aZnpRZ1bYmqL26daeI4V5EtGFkgChXD0XoKsIklUtw71GatoAQGT2VYpTD9sOIs+LNdLX6WpDIyV7LnKNWzaZks7bifVJ0lnRa+OK1+MM9vgKmk3ISXCLnfC4zKPxpgCcnfkV6g4=",
    exampleAnchor
  ))
  @adjustSchema(flatten)
  sealed trait Response

  def flatten(s: Schema): Schema = {
    s.copy(oneOf = s.oneOf.map {
      case RefOr.Value(v) => v.properties.head._2
      case x => x
    })
  }

  object Response extends RestDataCompanion[Response] {
    // adds custom status codes
    implicit def asRestResp(implicit
        successAsRaw: AsRaw[HttpBody, Success],
        failureAsRaw: AsRaw[HttpBody, Failure]
    ): AsRaw[RestResponse, Response] = {
      AsRaw.create {
        case s: Success => successAsRaw.asRaw(s).defaultResponse.recoverHttpError
        case NotFound => RestResponse(404, IMapping.empty, HttpBody.empty)
        case f: Failure => failureAsRaw.asRaw(f).defaultResponse.copy(code = 400).recoverHttpError
      }
    }
  }

  case class Success(upp: String, prev: String, anchors: Anchors) extends Response

  object Success extends RestDataCompanion[Success]

  // TODO: change that from JsonValue to something more appropriate when Carlos finishes the backend
  case class Anchors(json: JsonValue)

  object Anchors extends RestDataWrapperCompanion[JsonValue, Anchors] {
    implicit val schema: RestSchema[Anchors] = RestSchema.plain(Schema(`type` = DataType.Object))

    implicit def jsonNodeToAnchors(jsonNode: JsonNode): Anchors = Anchors(JsonValue(jsonNode.toString))
  }

  case object NotFound extends Response

  case class Failure(version: String = "1.0", status: String = "NOK", errorType: String = "ValidationError",
      errorMessage: String = "signature verification failed") extends Response

  object Failure extends RestDataCompanion[Failure]

}
