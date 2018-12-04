import java.util.{Date, UUID}

import com.ubirch.models.{Event, EventLog}
import org.json4s._
import org.json4s.jackson.Serialization

implicit lazy val formats = Serialization.formats(NoTypeHints) ++ org.json4s.ext.JavaTypesSerializers.all

val evl = EventLog(Event(UUID.randomUUID, "service_class", "cat"), "my-signature", new Date(), new Date())

jackson.prettyJson(Extraction.decompose(evl).camelizeKeys)

