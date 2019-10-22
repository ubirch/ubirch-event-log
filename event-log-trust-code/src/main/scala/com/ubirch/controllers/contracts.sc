import com.ubirch.models.TrustCode
import org.json4s.JsonDSL._

class CarContract extends TrustCode {
  def createCar(make: String, color: String, tagNumber: String, model: String): Unit = {

    val car = ("make" -> make)  ~ ("color" -> color)  ~ ("tag_number" -> tagNumber)  ~ ("model" -> model)
    put(tagNumber, car)

  }
}

scala.reflect.classTag[CarContract].runtimeClass
