package dev.one2.traceweave.mode

import dev.one2.traceweave.constant.Inplace
import java.util.Collections
import java.util.WeakHashMap

/**
 * INPLACE strategy: prepends a synthetic caller frame to the original exception's stack trace and
 * rethrows the same instance, leaving its type and identity intact.
 */
internal object InplaceMode : ModeStrategy {
  // Keyed by Throwable identity with weak references -- entries are dropped automatically once the
  // exception is no longer referenced elsewhere, so there is no memory leak. Synchronized because
  // coroutines hop threads: the same instance can reach weave() from different threads.
  private val frameDepths: MutableMap<Throwable, IntArray> =
    Collections.synchronizedMap(WeakHashMap())

  /**
   * Prepends a synthetic caller frame ([declaringClass].[methodName] at [fileName]:[lineNumber]) to
   * [error]'s stack trace, right below the throw site, and returns [error]. As the exception unwinds,
   * each outer caller is inserted one position lower, so the callee-to-caller order is preserved.
   *
   * Conflict-tolerant: if an identical frame already sits within [Inplace.DEDUP_RADIUS] of the
   * insertion point (whether put there by us or by another source) the insertion is skipped. The whole
   * read-counter / read-trace / write-trace sequence runs under the per-instance counter lock, because
   * coroutines hop threads and the same exception can reach here concurrently.
   */
  override fun weave(
    error: Throwable,
    declaringClass: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ): Throwable {
    val counter = synchronized(frameDepths) { frameDepths.getOrPut(error) { intArrayOf(0) } }
    synchronized(counter) {
      val st = error.stackTrace
      val next = counter[0] + 1
      val at = minOf(next, st.size)
      val newFrame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
      // Peek the position but only commit the counter when we actually insert: a skipped duplicate must
      // not advance it, or the next identical frame would land past this one's window and double up.
      if (frameAlreadyPresentNear(st, newFrame, at)) {
        return error
      }
      counter[0] = next
      error.stackTrace = st.copyOfRange(0, at) + newFrame + st.copyOfRange(at, st.size)
    }
    return error
  }

  // True when [frame] already appears within [Inplace.DEDUP_RADIUS] indices of [at] in [stackTrace].
  private fun frameAlreadyPresentNear(
    stackTrace: Array<StackTraceElement>,
    frame: StackTraceElement,
    at: Int,
  ): Boolean {
    val from = maxOf(0, at - Inplace.DEDUP_RADIUS)
    val to = minOf(stackTrace.size - 1, at + Inplace.DEDUP_RADIUS)
    for (i in from..to) {
      if (stackTrace[i] == frame) {
        return true
      }
    }
    return false
  }

  /** Test-only: drop the per-instance counters accumulated while weaving. */
  internal fun clearState() {
    frameDepths.clear()
  }
}
