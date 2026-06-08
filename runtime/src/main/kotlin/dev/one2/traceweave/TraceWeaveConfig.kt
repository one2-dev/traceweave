package dev.one2.traceweave

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable runtime policy. Owned by the application: set once via [configure], or bootstrapped from
 * `traceweave.*` system properties. Until a policy is present, traceweave is inert and [handle]
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

// System-property contract for the zero-config Gradle path (the plugin sets [PROP_ENABLED]).
internal const val PROP_PREFIX = "traceweave."
internal const val PROP_MODE = PROP_PREFIX + "mode"
internal const val PROP_ENABLED = PROP_PREFIX + "enabled"
private const val DISABLED_VALUE = "false"

private const val RECONFIGURE_WARNING =
  "traceweave: configure() called more than once; policy is application-owned and was replaced"

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
    System.err.println(RECONFIGURE_WARNING)
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

// Builds a policy from `traceweave.*` system properties, or returns `null` when none are set so we
// stay inert.
private fun bootstrapFromProperties(): TraceWeaveConfig? {
  val present = System.getProperties().stringPropertyNames().any { it.startsWith(PROP_PREFIX) }
  if (!present) {
    return null
  }
  if (System.getProperty(PROP_ENABLED)?.trim().equals(DISABLED_VALUE, ignoreCase = true)) {
    return null
  }
  val raw = System.getProperty(PROP_MODE)?.trim()
  val mode = Mode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Mode.INPLACE
  return TraceWeaveConfig(mode = mode)
}

/** Test-only: drop all runtime state so each test starts from the inert, unconfigured baseline. */
internal fun resetForTest() {
  configRef.set(null)
  bootstrapAttempted.set(false)
  clearInplaceState()
}
