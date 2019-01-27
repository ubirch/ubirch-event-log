package com.ubirch.services.lifeCycle

import java.util.concurrent.ConcurrentLinkedDeque

import com.typesafe.scalalogging.LazyLogging
import javax.inject._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Basic definition for a Life CyCle Component.
  * A component that supports StopHooks
  */
trait Lifecycle {

  def addStopHook(hook: () => Future[_]): Unit

  def stop(): Future[_]

}

/**
  * It represents the default implementation for the LifeCycle Component.
  * It actually executes or clears StopHooks.
  */

@Singleton
class DefaultLifecycle
  extends Lifecycle
  with LazyLogging {

  private val hooks = new ConcurrentLinkedDeque[() => Future[_]]()

  override def addStopHook(hook: () => Future[_]): Unit = hooks.push(hook)

  override def stop(): Future[_] = {

    @tailrec
    def clearHooks(previous: Future[Any] = Future.successful[Any](())): Future[Any] = {
      val hook = hooks.poll()
      if (hook != null) clearHooks(previous.flatMap { _ =>
        hook().recover {
          case e => logger.error("Error executing stop hook", e)
        }
      })
      else previous
    }

    logger.info("Running life cycle hooks...")
    clearHooks()
  }
}

/**
  * Definition for JVM ShutDown Hooks.
  */

trait JVMHook {
  protected def registerShutdownHooks(): Unit
}

/**
  * Default Implementation of the JVMHook.
  * It takes LifeCycle stop hooks and adds a corresponding shut down hook.
  * @param lifecycle LifeCycle Component that allows for StopDownHooks
  */

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

