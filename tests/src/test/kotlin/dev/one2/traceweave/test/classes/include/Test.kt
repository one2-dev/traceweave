package dev.one2.traceweave.test.classes.include

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
