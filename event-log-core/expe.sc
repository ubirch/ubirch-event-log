import com.ubirch.util.Implicits.enrichedDate
import org.joda.time.{DateTime, Minutes, Period, Seconds}

val currentTime = new DateTime()

Thread.sleep(1000)

val endTime = new DateTime()

Seconds.secondsBetween(currentTime, endTime).getSeconds
