package com.ubirch.verification.service.utils

import com.avsystem.commons.meta.MacroInstances
import com.avsystem.commons.rpc.AsRawReal
import io.udash.rest.openapi._
import io.udash.rest.raw.HttpBody
import io.udash.rest.raw.HttpBody.binary
import io.udash.rest.{ DefaultRestImplicits, OpenApiFullInstances, RestOpenApiCompanion, addResponseHeader }

package object udash {

  /**
    * Normally for methods which take an `Array[Byte]` as an argument udash accepts only requests which have
    * `application/octet-stream` content type. This class makes it so it accepts any content type and shows both
    * `text/plain` and `application/octet-stream` in swagger ui.
    */
  trait VerificationServiceRestImplicits extends DefaultRestImplicits {
    implicit val bodyForByteArray: AsRawReal[HttpBody, Array[Byte]] =
      AsRawReal.create(binary(_), body => body.readBytes( /* no content type specified */ ))

    implicit val ByteArrayMediaTypes: RestMediaTypes[Array[Byte]] =
      (resolver: SchemaResolver, schemaTransform: RestSchema[Array[Byte]] => RestSchema[_]) => {
        val schemaBinary = resolver.resolve(schemaTransform(RestSchema.plain(Schema.Binary)))
        val schemaText = resolver.resolve(schemaTransform(RestSchema.plain(Schema.String)))

        Map(
          HttpBody.OctetStreamType -> MediaType(schema = schemaBinary),
          HttpBody.PlainType -> MediaType(schema = schemaText)
        )
      }
  }

  object VerificationServiceRestImplicits extends VerificationServiceRestImplicits

  abstract class VerificationServiceRestApiCompanion[Real]
    (implicit inst: MacroInstances[VerificationServiceRestImplicits, OpenApiFullInstances[Real]])
    extends RestOpenApiCompanion[VerificationServiceRestImplicits, Real](VerificationServiceRestImplicits)

  class cors extends addResponseHeader("Access-Control-Allow-Origin", "*")

}
