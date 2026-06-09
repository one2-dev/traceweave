package dev.one2.traceweave.copier

/**
 * Rebuilds a fresh, writable instance of [original]'s type for COPY mode, chaining [original] as the
 * cause and carrying over its `message`. traceweave stamps the synthetic frames onto the returned copy
 * afterwards, so the copier must not touch the stack trace. Transferring suppressed exceptions and any
 * custom fields is the copier's responsibility.
 */
typealias ExceptionCopier = (original: Throwable) -> Throwable
