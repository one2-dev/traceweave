package dev.one2.traceweave.test.classes.recursive

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

suspend fun top(attempt: Int = 0) {
  if (attempt > 2) {
    error("Error")
  }
  delay(1.milliseconds)
  top(attempt + 1)
}
