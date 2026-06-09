package dev.one2.traceweave.constant

import dev.one2.traceweave.annotation.TraceWeave

object Copy {
  // Sentinel placed in a synthetic frame's declaringClass to mark "this is already our copy". It is
  // not a valid Java identifier, so no real class name collides with it -- which is why membership can
  // live in the stack trace itself instead of a global set.
  val MARKER = "--- @${TraceWeave::class.java.simpleName} ---"

  // Frames at or below these belong to the coroutine machinery, not user code. The seed (the
  // throw-site head copied onto a fresh copy) stops at the first such frame.
  const val COROUTINE_PACKAGE = "kotlinx.coroutines."
  const val CONTINUATION_CLASS = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
  const val SUSPEND_METHOD = "invokeSuspend"
}
