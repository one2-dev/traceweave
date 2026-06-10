package dev.one2.traceweave.test.classes.annotation

import dev.one2.traceweave.annotation.TraceWeave

@TraceWeave
class Clazz : Base {
  override suspend fun top() {
    super.top()
  }

  override suspend fun middle() {
    super.middle()
  }

  override suspend fun bot() {
    super.bot()
  }
}
