package dev.one2.traceweave.test.classes.annotation

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

interface Base {
  suspend fun top() {
    middle()
  }

  suspend fun middle() {
    bot()
  }

  suspend fun bot() {
    delay(1.milliseconds)
    error("Error")
  }
}
