package com.ubirch.dispatcher.services

import java.io.{BufferedReader, FileNotFoundException, IOException, InputStream, InputStreamReader}
import java.nio.file.{Files, Paths}

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.models.Dispatch
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util.EventLogJsonSupport

import scala.collection.JavaConverters._

import javax.inject._

@Singleton
class DispatchInfo @Inject() (config: Config) extends LazyLogging {

  lazy val info: List[Dispatch] = loadInfo.map(x => toDispatch(x)).getOrElse(Nil)
  val filePath: String = config.getString("eventLog.dispatching.path")
  val file: String = config.getString("eventLog.dispatching.file")

  private var fileStream: InputStream = _
  private var bufferReader: BufferedReader = _

  private def loadInfo: Option[String] = {
    try {

      val data = if(filePath.nonEmpty){

        val path = Paths.get(filePath, file)

        if (!path.toFile.exists()) {
          throw new FileNotFoundException("file not found " + file)
        } else {
          logger.info("Detected file {}", path.toAbsolutePath.toString)
        }

        Some(Files.readAllLines(path).asScala.toList)

      } else {

        logger.info("Reading from Resources")

        fileStream = getClass.getResourceAsStream("/" + file)
        bufferReader = new BufferedReader(new InputStreamReader(fileStream))

        val dispatch = bufferReader.lines().toArray.toList
        val value = dispatch.map(_.toString)

        Some(value)

      }

      data.map(_.mkString(" "))

    } catch {
      case e: IOException =>
        logger.error("Error parsing into Dispatch {}", e.getMessage)
        throw e

    } finally {
      if(bufferReader != null) bufferReader.close()
      if(fileStream != null) fileStream.close()
    }
  }

  private def toDispatch(data: String): List[Dispatch] = {
    try {
      val fromStringData = EventLogJsonSupport.FromString[List[Dispatch]](data)
      val compacted = EventLogJsonSupport.stringify(fromStringData.json)
      val di = fromStringData.get

      logger.info("Dispatching Info Found: {}", compacted)

      di.foreach { d =>
        if (d.category.isEmpty && d.category.length < 3) throw new IllegalArgumentException("Name can't be empty or has less than three letters")
        if (d.topics.isEmpty) throw new IllegalArgumentException("Topics can't be empty.")
        d.topics.foreach { dt =>
          if (dt.name.isEmpty && d.category.length < 3) throw new IllegalArgumentException("Name can't be empty or has less than three letters")
        }
      }
      if (di.isEmpty) throw new IllegalArgumentException("No valid Dispatching was found.")

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
