
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

var running = new AtomicBoolean(true)

var max = 1000

while(running.get){
  max
  max = max + 1

  if(max == 50){
    running.set(false)
  }

}

var out = "out"