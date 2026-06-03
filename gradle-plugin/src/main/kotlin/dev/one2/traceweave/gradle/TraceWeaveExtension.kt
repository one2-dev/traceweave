package dev.one2.traceweave.gradle

/**
 * DSL for the `dev.one2.trace-weave` plugin:
 * ```
 * traceWeave {
 *   classes = listOf("a.b.c", "a.b.c.*", "x.y.Z")
 *   excludeClasses = listOf("a.b.c.NoisyClass")
 * }
 * ```
 * Each entry in [classes] is a package or class FQN prefix; a trailing `.*` / `*` is stripped.
 * Functions whose declaring class (or package, for top-level functions) matches an entry get a
 * reconstructed coroutine stack frame.
 *
 * Entries in [excludeClasses] are excluded from tracing even when covered by [classes].
 * Exclusion takes priority over inclusion.
 */
open class TraceWeaveExtension {
  var enabled: Boolean = true
  var classes: List<String> = emptyList()
  var excludeClasses: List<String> = emptyList()
}
