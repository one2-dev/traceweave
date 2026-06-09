package dev.one2.traceweave.copier

import dev.one2.traceweave.extension.withCause

/**
 * Last-resort copier for COPY mode (opt-in via `reflectionCopy`): rebuilds [original]'s exact type by
 * probing the common `Throwable` constructor shapes, most message/cause-faithful first:
 * `(String, Throwable)` → `(String)` + initCause → `(Throwable)` → `()` + initCause.
 *
 * Returns `null` when none apply (no matching public constructor) or any reflection/init step throws,
 * so resolution falls through to the original. Does not touch the stack trace (CopyMode stamps it) nor
 * suppressed exceptions (CopyMode transfers those).
 */
internal fun reflectionCopy(original: Throwable): Throwable? {
  val type = original.javaClass
  val message = original.message
  return attempt { type.getConstructor(String::class.java, Throwable::class.java).newInstance(message, original) }
    ?: attempt { type.getConstructor(String::class.java).newInstance(message).withCause(original) }
    ?: attempt { type.getConstructor(Throwable::class.java).newInstance(original) }
    ?: attempt { type.getConstructor().newInstance().withCause(original) }
}

// Each attempt (construct + optional initCause) is guarded as a whole: a missing constructor or an
// already-set cause just yields null, so the chain tries the next shape rather than blowing up.
private inline fun attempt(build: () -> Throwable): Throwable? = runCatching(build).getOrNull()
