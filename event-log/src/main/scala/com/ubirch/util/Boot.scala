package com.ubirch.util

import com.google.inject.{ Guice, Injector }
import com.ubirch.services.ServiceBinder
import com.ubirch.services.lifeCycle.JVMHook

import scala.reflect._

trait InjectorHelper {

  private val injector: Injector = Guice.createInjector(new ServiceBinder())

  def get[T](clazz: Class[T]): T = injector.getInstance(clazz)

  def get[T](implicit ct: ClassTag[T]): T = get(ct.runtimeClass.asInstanceOf[Class[T]])

  def getAsOption[T](implicit ct: ClassTag[T]): Option[T] = Option(get(ct))

}

object InjectorHelper extends InjectorHelper

trait Boot extends InjectorHelper {

  private def bootJVMHook() = get[JVMHook]

  bootJVMHook()

}
