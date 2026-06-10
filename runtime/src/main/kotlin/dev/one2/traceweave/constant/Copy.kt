package dev.one2.traceweave.constant

import dev.one2.traceweave.annotation.TraceWeave

object Copy {
  // Sentinel placed in a synthetic frame's declaringClass to mark "this is already our copy". It is
  // not a valid Java identifier, so no real class name collides with it -- which is why membership can
  // live in the stack trace itself instead of a global set. Both marker frames share this class name;
  // their role rides in the fileName slot (see below).
  val MARKER = "--- @${TraceWeave::class.java.simpleName} ---"

  // Role of each marker, carried in its fileName so the trace reads `--- @TraceWeave ---.(weaved)` for
  // the reconstruction marker and `--- @TraceWeave ---.(by cause seed)` over the cause-seeded frames.
  const val WEAVE_LABEL = "weaved"
  const val SEED_LABEL = "by cause seed"

  // Default seed depth: how many leading throw-site frames COPY copies before the marker.
  const val DEFAULT_SEED_FRAMES = 1

  // Frames at or below these belong to the coroutine machinery, not user code. The seed (the
  // throw-site head copied onto a fresh copy) stops at the first such frame.
  const val COROUTINE_PACKAGE = "kotlinx.coroutines."
  const val CONTINUATION_CLASS = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
  const val SUSPEND_METHOD = "invokeSuspend"
}
