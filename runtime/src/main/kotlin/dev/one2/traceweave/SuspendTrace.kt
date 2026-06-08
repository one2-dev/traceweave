package dev.one2.traceweave

import java.util.Collections
import java.util.WeakHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val DEPRECATION_MESSAGE = "Replaced by handle(); emitted only by older plugin versions."

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
 * passed through. Best-effort: any failure inside leaves the original exception unchanged.
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
  } catch (_: Throwable) {
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
 */
private fun insertInplaceFrame(
  error: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
) {
  val counter = frameDepths.getOrPut(error) { intArrayOf(0) }
  val st = error.stackTrace
  val at = minOf(++counter[0], st.size)
  val newFrame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
  if (at < st.size && st[at] == newFrame) {
    return
  }
  error.stackTrace = st.copyOfRange(0, at) + newFrame + st.copyOfRange(at, st.size)
}

internal fun clearInplaceState() {
  frameDepths.clear()
}
