package com.ubirch.dispatcher.services

import java.io.{ BufferedReader, IOException, InputStream, InputStreamReader }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.models.Dispatch
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util.EventLogJsonSupport
import javax.inject._

@Singleton
class DispatchInfo @Inject() (config: Config) extends LazyLogging {

  lazy val info: List[Dispatch] = loadInfo.map(x => toDispatch(x)).getOrElse(Nil)
  val file: String = config.getString("eventLog.dispatching.file")
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
      val fromStringData = EventLogJsonSupport.FromString[List[Dispatch]](data)
      val compacted = EventLogJsonSupport.stringify(fromStringData.json)
      val di = fromStringData.get
      logger.debug("Dispatching Info Found: {}", compacted)
      di
    } catch {
      case e: Exception =>
        logger.error("Error parsing into Dispatch {}", e.getMessage)
        Nil
    }
  }

}

object DispatchInfo {

  def main(args: Array[String]): Unit = {

    val di = new DispatchInfo(new ConfigProvider get ())
    println("Current dispatching rules: \n" + di.info.zipWithIndex.map { case (d, i) => (i, d) }.mkString(", \n"))

  }
}
