package dev.one2.traceweave.config

import dev.one2.traceweave.constant.Configuration
import dev.one2.traceweave.constant.Message
import dev.one2.traceweave.logging.logger
import dev.one2.traceweave.mode.Mode
import dev.one2.traceweave.mode.clearCopyState
import dev.one2.traceweave.mode.clearInplaceState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable runtime policy. Owned by the application: set once via [configure], or bootstrapped from
 * `traceweave.*` system properties. Until a policy is present, traceweave is inert and `handle`
 * passes exceptions through untouched.
 *
 * This is the *policy* surface only (mode and, in later commits, logger/flags). Registering copiers
 * for specific exception types is a separate, additive surface so that libraries can contribute
 * without touching application-owned policy.
 */
class TraceWeaveConfig internal constructor(
  val mode: Mode,
)

/** Mutable builder backing the [configure] DSL. Policy only. */
class TraceWeaveConfigBuilder internal constructor() {
  var mode: Mode = Mode.INPLACE

  internal fun build(): TraceWeaveConfig = TraceWeaveConfig(mode = mode)
}

// Single source of truth for runtime policy. `null` == not configured == inert (pass-through).
private val configRef = AtomicReference<TraceWeaveConfig?>(null)

// Lazy property-bootstrap is attempted at most once, and only while still unconfigured.
private val bootstrapAttempted = AtomicBoolean(false)

/**
 * Installs the runtime [policy][TraceWeaveConfig]. Intended to be called once by the application at
 * startup; replaces any previous policy (last-wins).
 *
 * Policy is application-owned — a dependency should not call this (it would stomp the application's
 * mode/logger). A second call logs a soft warning rather than failing, since tests legitimately
 * reconfigure.
 */
fun configure(block: TraceWeaveConfigBuilder.() -> Unit) {
  val cfg = TraceWeaveConfigBuilder().apply(block).build()
  val previous = configRef.getAndSet(cfg)
  if (previous != null) {
    logger.warn(Message.RECONFIGURE_WARNING)
  }
}

/**
 * The active policy, or `null` when inert. On the first call while unconfigured, attempts a one-time
 * bootstrap from `traceweave.*` system properties (the zero-config Gradle path).
 */
internal fun activeConfig(): TraceWeaveConfig? {
  configRef.get()?.let { return it }
  if (bootstrapAttempted.compareAndSet(false, true)) {
    bootstrapFromProperties()?.let { configRef.compareAndSet(null, it) }
  }
  return configRef.get()
}

// Builds a policy from `traceweave.*` system properties. The activation gate is PROP_ENABLED (set by
// the Gradle plugin): absent -> inert, "false" -> inert. Otherwise PROP_MODE picks the mode.
private fun bootstrapFromProperties(): TraceWeaveConfig? {
  val enabled = System.getProperty(Configuration.PROP_ENABLED)?.trim()?.toBoolean() ?: false
  if (!enabled) {
    return null
  }
  val raw = System.getProperty(Configuration.PROP_MODE)?.trim()?.uppercase() ?: Mode.INPLACE.name
  val mode = Mode.valueOf(raw)
  return TraceWeaveConfig(mode = mode)
}

/** Test-only: drop all runtime state so each test starts from the inert, unconfigured baseline. */
internal fun resetForTest() {
  configRef.set(null)
  bootstrapAttempted.set(false)
  clearInplaceState()
  clearCopyState()
}
