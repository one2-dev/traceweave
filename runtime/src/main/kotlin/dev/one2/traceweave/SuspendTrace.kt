package dev.one2.traceweave

import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.WeakHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val DEPRECATION_MESSAGE = "Replaced by handle(); emitted only by older plugin versions."

// slf4j facade for traceweave's own diagnostics. No-op until the host app provides a binding.
internal const val LOGGER_NAME = "dev.one2.traceweave"
private val logger = LoggerFactory.getLogger(LOGGER_NAME)
private const val FRAME_INSERT_FAILED =
  "traceweave: frame insertion failed; original exception left unchanged"

// How far around the target index we look for an identical frame before inserting. A nonzero radius
// collapses duplicates contributed by other sources (coroutine recovery, DebugProbes) that land a
// position or two off from ours, not just an exact hit at the insertion index.
private const val DEDUP_RADIUS = 1

// Keyed by Throwable identity with weak references -- entries are dropped automatically once the
// exception is no longer referenced elsewhere, so there is no memory leak. Synchronized because
// coroutines hop threads: the same instance can reach handle() from different threads.
@PublishedApi
internal val frameDepths: MutableMap<Throwable, IntArray> =
  Collections.synchronizedMap(WeakHashMap())

/**
 * Runtime entry point called by plugin-generated code in the catch block around a suspend call:
 * `catch (e: Throwable) { throw handle(e, "com.x.C", "m", "C.kt", 42) }`.
 *
 * Returns the exception to actually rethrow — the same instance today (and in [Mode.INPLACE]); a
 * copy in [Mode.COPY] in a later commit.
 *
 * Until traceweave is configured (via [configure] or a `traceweave.*` property) this is a no-op
 * pass-through: the original exception is returned untouched. [CancellationException] is always
 * passed through. Best-effort: any failure inside leaves the original exception unchanged and is
 * reported through the slf4j logger [LOGGER_NAME] rather than propagated.
 */
fun handle(
  error: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
): Throwable {
  val config = activeConfig() ?: return error
  if (error is CancellationException) {
    return error
  }
  return try {
    when (config.mode) {
      Mode.INPLACE -> {
        insertInplaceFrame(error, declaringClass, methodName, fileName, lineNumber)
        error
      }
      // COPY is implemented in a later commit; pass through until then.
      Mode.COPY -> error
    }
  } catch (t: Throwable) {
    logger.warn(FRAME_INSERT_FAILED, t)
    error
  }
}

/**
 * Superseded by [handle]. Kept for binary compatibility with code compiled by older plugin versions,
 * which emit a direct call to this function. Always performs the [Mode.INPLACE] insertion regardless
 * of configuration, preserving the pre-`handle` behavior for already-compiled callers.
 */
@Deprecated(DEPRECATION_MESSAGE)
fun insertCoroutineFrame(
  error: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
) {
  if (error is CancellationException) {
    return
  }
  try {
    insertInplaceFrame(error, declaringClass, methodName, fileName, lineNumber)
  } catch (_: Throwable) {
    // frame insertion is best-effort -- never interfere with the original exception
  }
}

/**
 * Prepends a synthetic caller frame ([declaringClass].[methodName] at [fileName]:[lineNumber]) to
 * [error]'s stack trace, right below the throw site. As the exception unwinds, each outer caller is
 * inserted one position lower, so the callee-to-caller order is preserved.
 *
 * Conflict-tolerant: if an identical frame already sits within [DEDUP_RADIUS] of the insertion point
 * (whether put there by us or by another source) the insertion is skipped. The whole read-counter /
 * read-trace / write-trace sequence runs under the per-instance counter lock, because coroutines hop
 * threads and the same exception can reach here concurrently.
 */
private fun insertInplaceFrame(
  error: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
) {
  val counter = synchronized(frameDepths) { frameDepths.getOrPut(error) { intArrayOf(0) } }
  synchronized(counter) {
    val st = error.stackTrace
    val next = counter[0] + 1
    val at = minOf(next, st.size)
    val newFrame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
    // Peek the position but only commit the counter when we actually insert: a skipped duplicate must
    // not advance it, or the next identical frame would land past this one's window and double up.
    if (frameAlreadyPresentNear(st, newFrame, at)) {
      return
    }
    counter[0] = next
    error.stackTrace = st.copyOfRange(0, at) + newFrame + st.copyOfRange(at, st.size)
  }
}

// True when [frame] already appears within [DEDUP_RADIUS] indices of [at] in [stackTrace].
private fun frameAlreadyPresentNear(
  stackTrace: Array<StackTraceElement>,
  frame: StackTraceElement,
  at: Int,
): Boolean {
  val from = maxOf(0, at - DEDUP_RADIUS)
  val to = minOf(stackTrace.size - 1, at + DEDUP_RADIUS)
  for (i in from..to) {
    if (stackTrace[i] == frame) {
      return true
    }
  }
  return false
}

internal fun clearInplaceState() {
  frameDepths.clear()
}
