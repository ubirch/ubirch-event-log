package com.ubirch.util

import com.google.inject.{ Guice, Injector }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.ServiceBinder
import com.ubirch.services.lifeCycle.JVMHook
import com.ubirch.util.Exceptions.{ InjectionException, InjectorCreationException }

import scala.reflect._
import scala.util.Try

/**
  * Helper to manage Guice Injection.
  */
trait InjectorHelper extends LazyLogging {

  val modules = List(new ServiceBinder())

  private val injector: Injector = {
    try {
      Guice.createInjector(modules: _*)
    } catch {
      case e: Exception =>
        logger.error("Error Creating Injector: {} ", e.getMessage)
        throw InjectorCreationException(e.getMessage)
    }
  }

  def get[T](clazz: Class[T]): T = {
    try {
      injector.getInstance(clazz)
    } catch {
      case e: Exception =>
        logger.error("Error Injecting: {} ", e.getMessage)
        throw InjectionException(e.getMessage)
    }
  }

  def get[T](implicit ct: ClassTag[T]): T = get(ct.runtimeClass.asInstanceOf[Class[T]])

  def getAsOption[T](implicit ct: ClassTag[T]): Option[T] = Option(get(ct))

  def getAsTry[T](implicit ct: ClassTag[T]): Try[T] = Try(get(ct))

}

/**
  * Helper that allows to use the injection helper itself without
  * extending it or mixing it.
  */

object InjectorHelper extends InjectorHelper

/**
  * Util that integrates an elegant way to add shut down hooks to the JVM.
  */
trait Boot extends InjectorHelper {

  private def bootJVMHook() = get[JVMHook]

  bootJVMHook()

}
