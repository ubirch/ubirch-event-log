package com.ubirch.verification.controllers

import com.avsystem.commons.rpc.AsRaw
import com.fasterxml.jackson.databind.JsonNode
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.models.{ AnchorsNoPath, Normal }
import com.ubirch.verification.util.udash.{ VerificationServiceRestApiCompanion, cors }
import io.udash.rest.openapi.adjusters.{ adjustSchema, description, example, tags }
import io.udash.rest.openapi.{ DataType, RefOr, RestSchema, Schema }
import io.udash.rest.raw._
import io.udash.rest.{ Query, _ }

import scala.concurrent.Future
import scala.language.implicitConversions

trait V1 {
  @cors
  @CustomBody
  @POST("upp")
  @description(
    "This endpoint basically only queries for the existence of the upp. \n " +
      "It checks that it has been stored on our backend. No further checks are performed. You may think about this as a quick check."
  )
  @tags("v1")
  def getUPP(hash: Array[Byte], @Query disableRedisLookup: Boolean = false): Future[Api.Response]

  @cors
  @CustomBody // without that this api endpoint would expect json `{"payload": []}`
  @POST("upp/verify")
  @description("This query checks for the existence of the upp in our backend and additionally, it checks the \"chain\" and the validity of the \"keys\" (That the UPP can be verified by one of the available keys for the particualar device/entity.)")
  @tags("v1")
  def verifyUPP(hash: Array[Byte]): Future[Api.Response]

  @cors
  @CustomBody
  @POST("upp/verify/anchor")
  @description("This query checks for the existence of the upp in our backend, it checks the \"chain\" and the validity of the \"keys\" \n " +
    "(That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper bounds or the closet blockchains transactions in the near future. \n " +
    "You can get a compacted version or full version based on the params below.")
  @tags("v1")
  def verifyUPPWithUpperBound(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value
  ): Future[Api.Response]

  @cors
  @CustomBody
  @POST("upp/verify/record")
  @description("This query checks for the existence of the upp in our backend and additionally, it checks the \"chain\" and the validity of the \"keys\" \n " +
    "(That the UPP can be verified by one of the available keys for the particualar device/entity.)\n\n" +
    "This query checks for the existence of the upp in our backend, it checks the \"chain\" and the validity of the \"keys\" " +
    "\n (That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper and lower bounds or the closet blockchains transactions in the near future and past. \n" +
    "You can get a compacted version or full version based on the params below.")
  @tags("v1")
  def verifyUPPWithUpperAndLowerBound(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value
  ): Future[Api.Response]
}

trait V2 {
  @cors
  @CustomBody
  @POST("v2/upp")
  @description(
    "This endpoint basically only queries for the existence of the upp. \n " +
      "It checks that it has been stored on our backend. No further checks are performed. You may think about this as a quick check."
  )
  @tags("v2")
  def getUPPV2(
      hash: Array[Byte],
      @Query disableRedisLookup: Boolean = false,
      @Header("authorization") authToken: String = "No-Header-Found",
      @Header("origin") origin: String = "No-Header-Found"
  ): Future[Api.Response]

  @cors
  @CustomBody // without that this api endpoint would expect json `{"payload": []}`
  @POST("v2/upp/verify")
  @description("This query checks for the existence of the upp in our backend and additionally, it checks the \"chain\" and the validity of the \"keys\" (That the UPP can be verified by one of the available keys for the particualar device/entity.)")
  @tags("v2")
  def verifyUPPV2(
      hash: Array[Byte],
      @Header("authorization") authToken: String = "No-Header-Found",
      @Header("origin") origin: String = "No-Header-Found"
  ): Future[Api.Response]

  @cors
  @CustomBody
  @POST("v2/upp/verify/anchor")
  @description("This query checks for the existence of the upp in our backend, it checks the \"chain\" and the validity of the \"keys\" \n " +
    "(That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper bounds or the closet blockchains transactions in the near future. \n " +
    "You can get a compacted version or full version based on the params below.")
  @tags("v2")
  def verifyUPPWithUpperBoundV2(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value,
      @Header("authorization") authToken: String = "No-Header-Found",
      @Header("origin") origin: String = "No-Header-Found"
  ): Future[Api.Response]

  @cors
  @CustomBody
  @POST("v2/upp/verify/record")
  @description("This query checks for the existence of the upp in our backend and additionally, it checks the \"chain\" and the validity of the \"keys\" \n " +
    "(That the UPP can be verified by one of the available keys for the particualar device/entity.)\n\n" +
    "This query checks for the existence of the upp in our backend, it checks the \"chain\" and the validity of the \"keys\" " +
    "\n (That the UPP can be verified by one of the available keys for the particualar device/entity) and retrieves the upper and lower bounds or the closet blockchains transactions in the near future and past. \n" +
    "You can get a compacted version or full version based on the params below.")
  @tags("v2")
  def verifyUPPWithUpperAndLowerBoundV2(
      hash: Array[Byte],
      @Query("response_form") responseForm: String = AnchorsNoPath.value,
      @Query("blockchain_info") blockchainInfo: String = Normal.value,
      @Header("authorization") authToken: String = "No-Header-Found",
      @Header("origin") origin: String = "No-Header-Found"
  ): Future[Api.Response]

}

trait Api extends V1 with V2 {

  @cors
  @GET
  @tags("health")
  def health: Future[String]

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

    val OK: Int = 200
    val BAD_REQUEST: Int = 400
    val NOT_FOUND: Int = 404
    val UNAUTHORIZED: Int = 401
    val FORBIDDEN: Int = 403

    // adds custom status codes
    implicit def asRestResp(implicit
        successAsRaw: AsRaw[HttpBody, Success],
        failureAsRaw: AsRaw[HttpBody, Failure]
    ): AsRaw[RestResponse, Response] = {
      AsRaw.create {
        case s: Success => successAsRaw.asRaw(s).defaultResponse.recoverHttpError
        case NotFound => RestResponse(NOT_FOUND, IMapping.empty, HttpBody.empty)
        case AuthorizationHeaderNotFound => RestResponse(UNAUTHORIZED, IMapping("WWW-Authenticate" -> PlainValue("""Bearer realm="Verification Access" """.trim)), HttpBody.empty)
        case Forbidden => RestResponse(FORBIDDEN, IMapping.empty, HttpBody.empty)
        case f: Failure => failureAsRaw.asRaw(f).defaultResponse.copy(code = BAD_REQUEST).recoverHttpError
      }
    }
  }

  case class Anchors(json: JsonValue)
  object Anchors extends RestDataWrapperCompanion[JsonValue, Anchors] {
    implicit val schema: RestSchema[Anchors] = RestSchema.plain(Schema(`type` = DataType.Object))

    implicit def jsonNodeToAnchors(jsonNode: JsonNode): Anchors = Anchors(JsonValue(jsonNode.toString))
  }
  case class Success(upp: String, prev: String, anchors: Anchors) extends Response
  object Success extends RestDataCompanion[Success]

  case object AuthorizationHeaderNotFound extends Response
  case object Forbidden extends Response
  case object NotFound extends Response
  case class Failure(version: String = "1.0", status: String = "NOK", errorType: String = "ValidationError", errorMessage: String = "signature verification failed") extends Response
  object Failure extends RestDataCompanion[Failure]

  case class DecoratedResponse(protocolMessage: Option[ProtocolMessage], response: Response) {
    def isSuccess: Boolean = {
      response match {
        case Success(_, _, _) => true
        case _ => false
      }
    }
  }
  object DecoratedResponse {

    case class Decoration(response: Response) {
      def withNoPM: DecoratedResponse = DecoratedResponse(None, response)
      def boundPM(protocolMessage: ProtocolMessage): DecoratedResponse = DecoratedResponse(Option(protocolMessage), response)
    }

    implicit def toDecoration(response: Response): Decoration = Decoration(response)

  }

}
