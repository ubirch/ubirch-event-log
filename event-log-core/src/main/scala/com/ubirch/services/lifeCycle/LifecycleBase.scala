package com.ubirch.services.lifeCycle

import java.util.concurrent.{ ConcurrentLinkedDeque, CountDownLatch, TimeUnit }

import com.typesafe.scalalogging.LazyLogging
import javax.inject._

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
  * Basic definition for a Life CyCle Component.
  * A component that supports StopHooks
  */
trait Lifecycle {

  def addStopHook(hook: () => Future[_]): Unit

  def addStopHooks(hooks: (() => Future[_])*): Unit = hooks.foreach(addStopHook)

  def stop(): Future[_]

}

/**
  * It represents the default implementation for the LifeCycle Component.
  * It actually executes or clears StopHooks.
  */

@Singleton
class DefaultLifecycle @Inject() (implicit ec: ExecutionContext)
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
class DefaultJVMHook @Inject() (lifecycle: Lifecycle)(implicit ec: ExecutionContext) extends JVMHook with LazyLogging {

  protected def registerShutdownHooks() {

    logger.info("Registering Shutdown Hooks")

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        val countDownLatch = new CountDownLatch(1)
        lifecycle.stop().onComplete {
          case Success(_) =>
            countDownLatch.countDown()
          case Failure(e) =>
            logger.error("Error running jvm hook={}", e.getMessage)
            countDownLatch.countDown()
        }

        val res = countDownLatch.await(5, TimeUnit.SECONDS) //Waiting 5 secs
        if (!res) logger.warn("Taking too much time shutting down :(  ..")
        else logger.info("Bye bye, see you later...")
      }
    })

  }

  registerShutdownHooks()

}

