package dev.one2.traceweave.test.classes.annotation

import dev.one2.traceweave.annotation.TraceWeave

class Method : Base {
  @TraceWeave
  override suspend fun top() {
    super.top()
  }

  @TraceWeave
  override suspend fun middle() {
    super.middle()
  }

  @TraceWeave
  override suspend fun bot() {
    super.bot()
  }
}
