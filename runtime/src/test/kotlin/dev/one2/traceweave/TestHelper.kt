package dev.one2.traceweave

import dev.one2.traceweave.handler.handle

// Shared literals and factories for the runtime tests, so the same message and synthetic frame
// coordinates are not re-typed in every test.
internal object TestHelper {
  const val MESSAGE = "boom"
  const val CLASS = "Outer"
  const val METHOD = "outer"
  const val FILE = "Test.kt"
  const val LINE = 10

  // A plain failure carrying the shared message.
  fun error(message: String = MESSAGE): RuntimeException = RuntimeException(message)

  // handle() at the shared default frame coordinates.
  fun handleDefault(error: Throwable): Throwable = handle(error, CLASS, METHOD, FILE, LINE)
}
