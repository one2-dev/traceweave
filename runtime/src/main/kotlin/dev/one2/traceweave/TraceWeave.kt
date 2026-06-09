package dev.one2.traceweave

import dev.one2.traceweave.constant.Configuration
import dev.one2.traceweave.constant.Message
import dev.one2.traceweave.copier.ExceptionCopier
import dev.one2.traceweave.copier.TraceWeaveCopierProvider
import dev.one2.traceweave.logging.logger
import dev.one2.traceweave.mode.CopyMode
import dev.one2.traceweave.mode.InplaceMode
import dev.one2.traceweave.mode.Mode
import dev.one2.traceweave.mode.ModeStrategy
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * The single runtime entry point to traceweave. Everything lives here:
 *
 * - **policy** — [configure] installs the application-owned mode/strategy. Until then traceweave is
 *   inert and [handle] passes exceptions through untouched.
 * - **copier registry** — [register] / [unregister] / [entry] / [loadCopiers] contribute per-type
 *   copiers for COPY mode. Additive and safe from anywhere (incl. libraries): never activates the
 *   runtime or touches policy. An explicit [register] overrides any [TraceWeaveCopierProvider].
 * - **handler** — [handle] is the hot-path entry the compiler plugin generates calls to.
 */
object TraceWeave {
  // ===== policy =====

  // Single source of truth for runtime policy. `null` == not configured == inert (pass-through).
  private val configRef = AtomicReference<TraceWeaveConfig?>(null)

  // Lazy property-bootstrap is attempted at most once, and only while still unconfigured.
  private val bootstrapAttempted = AtomicBoolean(false)

  /**
   * Installs the runtime [policy][TraceWeaveConfig] via the [TraceWeaveConfigBuilder] DSL. Intended to
   * be called once by the application at startup; replaces any previous policy (last-wins).
   *
   * Policy is application-owned — a dependency should not call this (it would stomp the application's
   * mode/strategy). A second call logs a soft warning rather than failing, since tests legitimately
   * reconfigure.
   */
  fun configure(block: TraceWeaveConfigBuilder.() -> Unit) {
    val builder = TraceWeaveConfigBuilder().apply(block)
    val strategy = builder.strategy
      ?: builder.mode.builtInStrategy()
      ?: error("Mode.CUSTOM requires a strategy; set one via configure { strategy = ... }")
    val cfg = TraceWeaveConfig(strategy, builder.reflectionCopy)
    if (configRef.getAndSet(cfg) != null) {
      logger.warn(Message.RECONFIGURE_WARNING)
    }
  }

  // The active policy, or `null` when inert. On the first call while unconfigured, attempts a one-time
  // bootstrap from `traceweave.*` system properties (the zero-config Gradle path).
  internal fun activeConfig(): TraceWeaveConfig? {
    configRef.get()?.let { return it }
    if (bootstrapAttempted.compareAndSet(false, true)) {
      bootstrapFromProperties()?.let { configRef.compareAndSet(null, it) }
    }
    return configRef.get()
  }

  /** Whether COPY mode may fall back to the reflection copier; off unless policy enables it. */
  internal fun reflectionCopyEnabled(): Boolean = activeConfig()?.reflectionCopy ?: false

  // Builds a policy from `traceweave.*` system properties. The activation gate is PROP_ENABLED (set by
  // the Gradle plugin): absent -> inert, "false" -> inert. Otherwise PROP_MODE picks a built-in mode;
  // CUSTOM is rejected here since a strategy cannot be supplied through a property.
  private fun bootstrapFromProperties(): TraceWeaveConfig? {
    val enabled = System.getProperty(Configuration.PROP_ENABLED)?.trim()?.toBoolean() ?: false
    if (!enabled) {
      return null
    }
    val raw = System.getProperty(Configuration.PROP_MODE)?.trim()?.uppercase() ?: Mode.INPLACE.name
    val reflectionCopy = System.getProperty(Configuration.PROP_REFLECTION)?.trim()?.toBoolean() ?: false
    // CUSTOM has no built-in strategy, so it stays inert here -- a custom strategy can only be supplied
    // in code via configure {}.
    return Mode.valueOf(raw).builtInStrategy()?.let { TraceWeaveConfig(strategy = it, reflectionCopy = reflectionCopy) }
  }

  // The built-in strategy for a mode, or null for CUSTOM (whose strategy is supplied via configure).
  private fun Mode.builtInStrategy(): ModeStrategy? =
    when (this) {
      Mode.INPLACE -> InplaceMode
      Mode.COPY -> CopyMode
      Mode.CUSTOM -> null
    }

  // ===== handler (hot path; the compiler plugin generates calls here) =====

  /**
   * Runtime entry point called by plugin-generated code in the catch block around a suspend call:
   * `catch (e: Throwable) { throw TraceWeave.handle(e, "com.x.C", "m", "C.kt", 42) }`.
   *
   * Returns the exception to actually rethrow, as decided by the active mode's strategy: the same
   * instance for in-place weaving, a fresh copy (original chained as its cause) for copy weaving, or
   * whatever a custom strategy produces.
   *
   * Until traceweave is configured (via [configure] or a `traceweave.*` property) this is a no-op
   * pass-through: the original exception is returned untouched. [CancellationException] and
   * [VirtualMachineError] are always passed through (the latter because allocating a copy or array
   * while the VM is already failing only makes things worse). Best-effort: any failure inside leaves
   * the original exception unchanged and is reported through the slf4j logger rather than propagated.
   */
  fun handle(
    error: Throwable,
    declaringClass: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ): Throwable {
    if (error is CancellationException || error is VirtualMachineError) {
      return error
    }

    val config = activeConfig() ?: return error

    return try {
      config.strategy.weave(error, declaringClass, methodName, fileName, lineNumber)
    } catch (t: Throwable) {
      logger.warn(Message.FRAME_INSERT_FAILED, t)
      error
    }
  }

  // ===== copier registry =====

  // Copiers keyed by exact class. Queried only in COPY mode, so registering one is side-effect-free and
  // never activates traceweave.
  private val registry = ConcurrentHashMap<Class<*>, ExceptionCopier>()

  // Types already claimed by a ServiceLoader provider, so a second provider for the same type can be
  // detected and warned about. Separate from `registry` so an explicit app registration (which
  // overrides a provider) is not mistaken for a provider-vs-provider conflict.
  private val providerClaimed: MutableSet<Class<*>> = ConcurrentHashMap.newKeySet()

  // Providers are discovered lazily, exactly once, on the first lookup under COPY mode.
  private val providersLoaded = AtomicBoolean(false)

  /**
   * Registers a [copier] for exception type [T], used by COPY mode to rebuild it with full type/field
   * fidelity. Intended for 3rd-party types you do not own (for your own types, implement
   * [dev.one2.traceweave.exception.TraceWeaveException] instead). An explicit call here overrides any
   * [TraceWeaveCopierProvider] contribution for the same type.
   */
  inline fun <reified T : Throwable> register(noinline copier: (original: T) -> Throwable) {
    @Suppress("UNCHECKED_CAST")
    register(T::class.java, copier as ExceptionCopier)
  }

  /** Non-reified backing for [register]; also the entry point a provider's entries flow through. */
  fun register(
    type: Class<out Throwable>,
    copier: ExceptionCopier,
  ) {
    // Explicit registration always wins, even over a provider loaded earlier.
    registry[type] = copier
  }

  /** Drops the copier for [T], whether registered explicitly or supplied by a provider. */
  inline fun <reified T : Throwable> unregister() = unregister(T::class.java)

  /** Non-reified backing for [unregister]. */
  fun unregister(type: Class<out Throwable>) {
    registry.remove(type)
  }

  /**
   * Type-safe entry builder for a [TraceWeaveCopierProvider.copiers] map, so providers avoid an
   * unchecked cast of the `original` parameter: `mapOf(entry(MyException::class.java) { o -> ... })`.
   */
  fun <T : Throwable> entry(
    type: Class<T>,
    copier: (original: T) -> Throwable,
  ): Pair<Class<out Throwable>, ExceptionCopier> {
    @Suppress("UNCHECKED_CAST")
    return type to (copier as ExceptionCopier)
  }

  /** The copier registered for [type] (explicit or provider-supplied), or `null`. COPY mode only. */
  internal fun lookup(type: Class<*>): ExceptionCopier? {
    loadCopiers()
    return registry[type]
  }

  /**
   * Discovers [TraceWeaveCopierProvider]s via [ServiceLoader] and folds their copiers into the
   * registry. Runs at most once (subsequent calls are no-ops); normally triggered lazily by the first
   * [lookup], but safe to call eagerly. Uses putIfAbsent so an explicit [register] already in place
   * keeps priority; a second provider for an already-claimed type is logged and dropped.
   */
  fun loadCopiers() {
    if (!providersLoaded.compareAndSet(false, true)) {
      return
    }
    val providerType = TraceWeaveCopierProvider::class.java
    for (provider in ServiceLoader.load(providerType, providerType.classLoader)) {
      for ((type, copier) in provider.copiers()) {
        if (!providerClaimed.add(type)) {
          logger.warn(Message.duplicateCopierProvider(type))
          continue
        }
        registry.putIfAbsent(type, copier)
      }
    }
  }

  /** Test-only: drop all runtime state so each test starts from the inert, unconfigured baseline. */
  internal fun reset() {
    configRef.set(null)
    bootstrapAttempted.set(false)
    InplaceMode.clearState()
    CopyMode.clearState()
    registry.clear()
    providerClaimed.clear()
    providersLoaded.set(false)
  }
}

/**
 * Immutable runtime policy. Owned by the application: set once via [TraceWeave.configure], or
 * bootstrapped from `traceweave.*` system properties. This is the *policy* surface only (the active
 * [strategy] and the [reflectionCopy] flag). Registering copiers is a separate, additive surface.
 */
class TraceWeaveConfig internal constructor(
  val strategy: ModeStrategy,
  val reflectionCopy: Boolean,
)

/** Mutable builder backing the [TraceWeave.configure] DSL. Policy only. */
class TraceWeaveConfigBuilder internal constructor() {
  /** Built-in mode to use; ignored once a custom [strategy] is set. */
  var mode: Mode = Mode.INPLACE

  /**
   * A custom [ModeStrategy] that takes over how caught exceptions are woven. When set, it overrides
   * [mode] entirely (equivalent to [Mode.CUSTOM]) -- do whatever you like with the exception.
   */
  var strategy: ModeStrategy? = null

  /**
   * Lets COPY mode rebuild an unowned exception type by reflection when no interface/registered/built-in
   * copier applies. Off by default (best-effort, slower); also settable via `traceweave.copy.reflection`.
   */
  var reflectionCopy: Boolean = false
}
