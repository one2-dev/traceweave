package dev.one2.traceweave.mode

/**
 * Runtime strategy for reconstructing a coroutine call chain.
 *
 * - [INPLACE] mutates the original exception's stack trace and rethrows the same instance.
 * - [COPY] writes frames into a fresh copy of the same type, leaving the original as the cause.
 * - [CUSTOM] hands control to a [ModeStrategy] you supply via `configure { strategy = ... }`, so you
 *   can weave frames however you like; setting a `strategy` already implies this mode.
 *
 * Selected via `configure` or the `traceweave.mode` system property (`inplace` / `copy`). [CUSTOM]
 * cannot be selected through the property -- a strategy can only be supplied in code.
 */
enum class Mode {
  INPLACE,
  COPY,
  CUSTOM,
}
