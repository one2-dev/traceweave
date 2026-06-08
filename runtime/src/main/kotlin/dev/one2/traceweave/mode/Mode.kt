package dev.one2.traceweave.mode

/**
 * Runtime strategy for reconstructing a coroutine call chain.
 *
 * - [INPLACE] mutates the original exception's stack trace and rethrows the same instance.
 * - [COPY] writes frames into a fresh copy of the same type, leaving the original as the cause.
 *
 * Selected via `configure` or the `traceweave.mode` system property (`inplace` / `copy`).
 */
enum class Mode {
  INPLACE,
  COPY,
}
