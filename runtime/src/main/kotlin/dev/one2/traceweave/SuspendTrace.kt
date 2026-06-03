package dev.one2.traceweave

import java.util.Collections
import java.util.WeakHashMap
import kotlin.coroutines.cancellation.CancellationException

// Keyed by Throwable identity with weak references -- entries are dropped automatically once the
// exception is no longer referenced elsewhere, so there is no memory leak.
@PublishedApi
internal val frameDepths: MutableMap<Throwable, IntArray> =
  Collections.synchronizedMap(WeakHashMap())

/**
 * Called by plugin-generated code in the catch block around a suspend call.
 *
 * Prepends a synthetic caller frame ([declaringClass].[methodName] at [fileName]:[lineNumber]) to
 * [error]'s stack trace, right below the throw site. As the exception unwinds, each outer caller
 * is inserted one position lower, so the callee-to-caller order is preserved.
 *
 * A [CancellationException] is passed through untouched.
 */
fun insertCoroutineFrame(
  error: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
) {
  if (error is CancellationException) return
  try {
    val counter = frameDepths.getOrPut(error) { intArrayOf(0) }
    val st = error.stackTrace
    val at = minOf(++counter[0], st.size)
    val newFrame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
    if (at < st.size && st[at] == newFrame) return
    error.stackTrace = st.copyOfRange(0, at) + newFrame + st.copyOfRange(at, st.size)
  } catch (_: Throwable) {
    // frame insertion is best-effort -- never interfere with the original exception
  }
}
