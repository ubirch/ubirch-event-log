package com.ubirch.services.lifeCycle

import java.util.concurrent.ConcurrentLinkedDeque

import com.typesafe.scalalogging.LazyLogging
import javax.inject._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Lifecycle {

  def addStopHook(hook: () ⇒ Future[_]): Unit

  def stop(): Future[_]

}

@Singleton
class DefaultLifecycle
    extends Lifecycle
    with LazyLogging {

  private val hooks = new ConcurrentLinkedDeque[() ⇒ Future[_]]()

  override def addStopHook(hook: () ⇒ Future[_]): Unit = hooks.push(hook)

  override def stop(): Future[_] = {

    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] = {
      val hook = hooks.poll()
      if (hook != null) clearHooks(previous.flatMap { _ ⇒
        hook().recover {
          case e ⇒ logger.error("Error executing stop hook", e)
        }
      })
      else previous
    }

    logger.info("Running life cycle hooks...")
    clearHooks()
  }
}

trait JVMHook {
  protected def registerShutdownHooks(): Unit
}

@Singleton
class DefaultJVMHook @Inject() (lifecycle: Lifecycle) extends JVMHook with LazyLogging {

  protected def registerShutdownHooks() {

    logger.info("Registering Shutdown Hooks")

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        lifecycle.stop()

        Thread.sleep(5000) //Waiting 5 secs
        logger.info("Bye bye, see you later...")
      }
    })

  }

  registerShutdownHooks()

}

