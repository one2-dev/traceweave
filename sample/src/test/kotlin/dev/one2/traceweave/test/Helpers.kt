package dev.one2.traceweave.test

fun Throwable.hasFrames(vararg methodNames: String): Boolean {
  val indices = methodNames.reversed().map { name -> stackTrace.indexOfFirst { it.methodName == name } }
  return indices.all { it >= 0 } && indices == indices.sorted()
}
