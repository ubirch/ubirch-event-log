val x: PartialFunction[Int, String] = {
  case x if x > 2 => "Hola"
}


val y = new PartialFunction[Int, String] {
  override def isDefinedAt(x: Int) = ???
  override def apply(v1: Int) = ???
}
