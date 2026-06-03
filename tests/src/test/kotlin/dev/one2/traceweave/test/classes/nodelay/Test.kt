package dev.one2.traceweave.test.classes.nodelay

suspend fun top() {
  middle()
}

suspend fun middle() {
  bot()
}

suspend fun bot() {
  error("Error")
}
