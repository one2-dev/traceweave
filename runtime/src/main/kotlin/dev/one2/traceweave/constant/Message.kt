package dev.one2.traceweave.constant

// Human-readable diagnostics emitted through the logger.
object Message {
  const val RECONFIGURE_WARNING =
    "traceweave: configure() called more than once; policy is application-owned and was replaced"
  const val FRAME_INSERT_FAILED =
    "traceweave: frame insertion failed; original exception left unchanged"

  fun duplicateCopierProvider(type: Class<*>): String =
    "traceweave: more than one copier provider for ${type.name}; keeping the first"
}
