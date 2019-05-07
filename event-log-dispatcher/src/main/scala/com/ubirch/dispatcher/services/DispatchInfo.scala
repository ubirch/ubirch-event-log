package com.ubirch.dispatcher.services

import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.models.Dispatch
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Implicits.enrichedConfig
import javax.inject._

@Singleton
class DispatchInfo @Inject() (config: Config) extends LazyLogging {

  lazy val info: List[Dispatch] = loadInfo.map(x => toDispatch(x)).getOrElse(Nil)
  val file: String = config.getStringAsOption("eventLog.dispatching.file").getOrElse("DispatchingPaths.json")
  private var fileStream: InputStream = _
  private var bufferReader: BufferedReader = _

  private def loadInfo: Option[String] = {
    try {

      fileStream = getClass.getResourceAsStream("/" + file)
      bufferReader = new BufferedReader(new InputStreamReader(fileStream))

      val dispatch = bufferReader.lines().toArray.toList
      val value = dispatch.map(_.toString).mkString(" ")

      Some(value)

    } catch {
      case e: IOException =>
        logger.error("Error parsing into Dispatch {}", e.getMessage)
        None
    } finally {
      bufferReader.close()
      fileStream.close()
    }
  }

  private def toDispatch(data: String): List[Dispatch] = {
    try {
      EventLogJsonSupport.FromString[List[Dispatch]](data).get
    } catch {
      case e: Exception =>
        logger.error("Error parsing into Dispatch {}", e.getMessage)
        Nil
    }
  }

}
