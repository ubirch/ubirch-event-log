import scala.reflect.runtime.{universe => ru}

case class Person(name: String)

val m = ru.runtimeMirror(getClass.getClassLoader)
val classPerson = ru.typeOf[Person].typeSymbol.asClass
val cm = m.reflectClass(classPerson)
val ctor = ru.typeOf[Person].decl(ru.termNames.CONSTRUCTOR).asMethod
val ctorm = cm.reflectConstructor(ctor)
val p = ctorm("Mike").asInstanceOf[Person]
p.name