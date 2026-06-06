package dev.one2.traceweave.test.classes.annotation

import dev.one2.traceweave.TraceWeave
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@TraceWeave
suspend fun top() {
  middle()
}

@TraceWeave
suspend fun middle() {
  bot()
}

@TraceWeave
suspend fun bot() {
  delay(1.milliseconds)
  error("Error")
}
