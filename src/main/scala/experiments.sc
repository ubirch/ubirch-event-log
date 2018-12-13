


import java.util.{Date, UUID}

import com.ubirch.models.{Event, EventLog, TimeInfo}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.json4s.jackson.Serialization

implicit lazy val formats = Serialization.formats(NoTypeHints) ++
  org.json4s.ext.JavaTypesSerializers.all

val data = parse(""" { "numbers" : [1, 2, 3, 4] } """)

val event = EventLog(Event(
  UUID.randomUUID(),
  "this is a service class",
  "this is a category",
  data,
  new Date(),
  TimeInfo(2018, 12, 12, 12, 12, 12, 12)
),
  "this is a signature",
  new Date(),
  new Date()
)





val t = Extraction.decompose(event).underscoreKeys

jackson.compactJson(t)



