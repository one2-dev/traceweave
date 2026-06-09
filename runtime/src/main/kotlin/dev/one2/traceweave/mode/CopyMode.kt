package dev.one2.traceweave.mode

import dev.one2.traceweave.TraceWeave
import dev.one2.traceweave.constant.Copy
import dev.one2.traceweave.copier.BUILTIN_COPIERS
import dev.one2.traceweave.copier.reflectionCopy
import dev.one2.traceweave.exception.TraceWeaveException
import dev.one2.traceweave.extension.isCoroutineMachinery
import dev.one2.traceweave.extension.wasCopied
import dev.one2.traceweave.logging.logger
import java.util.Collections

/**
 * COPY strategy: writes the synthetic frame onto a fresh copy of the same type (with the original
 * chained as cause) and rethrows the copy, leaving the original exception untouched.
 */
internal object CopyMode : ModeStrategy {
  private val MARKER_FRAME = StackTraceElement(Copy.MARKER, "", null, -1)

  // One warning per class we fall back on, so a recurring unsupported/misbehaving type cannot flood the
  // log. A given class always fails the same way, so a single entry per class is enough.
  private val warnedClasses: MutableSet<Class<*>> = Collections.synchronizedSet(HashSet())

  /**
   * COPY-mode counterpart of [InplaceMode.weave]. Writes the synthetic frame onto a fresh copy of the
   * same type (with the original chained as cause) and returns it, leaving the original untouched. On
   * the next unwind level the copy is recognised by its [Copy.MARKER] frame and the new frame is
   * appended, so a whole chain produces exactly one copy. Falls back to the original whenever a copy
   * cannot be made (no copier, or the copier yields an unwritable instance).
   */
  override fun weave(
    error: Throwable,
    declaringClass: String,
    methodName: String,
    fileName: String,
    lineNumber: Int,
  ): Throwable {
    val frame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
    if (error.wasCopied()) {
      error.stackTrace += frame
      return error
    }
    val copy = tryCopy(error)
    if (copy == null) {
      warnOnce(error) { "traceweave: no copier for ${error.javaClass.name}; using the original" }
      return error
    }
    copy.stackTrace = seed(error) + MARKER_FRAME + frame
    if (copy.stackTrace.isEmpty()) {
      // An unwritable copy (writableStackTrace = false) silently drops the assignment above.
      warnOnce(error) {
        "traceweave: copy of ${error.javaClass.name} came back without a writable stack trace; " +
          "using the original"
      }
      return error
    }
    return copy
  }

  // The real throw-site head of [flying], up to (but not including) the first coroutine-machinery
  // frame, so the copy's own trace points at where it was thrown; the full original lives in the cause.
  private fun seed(flying: Throwable): Array<StackTraceElement> {
    val st = flying.stackTrace
    val cut = st.indexOfFirst { it.isCoroutineMachinery() }
    return if (cut < 0) st else st.copyOfRange(0, cut)
  }

  // Resolution order: owned interface -> registered copier (app/library) -> built-in table ->
  // reflection (opt-in via reflectionCopy) -> null. null means "no copier" only; a copier that *throws*
  // is left to propagate to the handler's best-effort catch, which logs it with its cause and returns
  // the original -- so a failure is never swallowed silently here.
  private fun tryCopy(flying: Throwable): Throwable? {
    (flying as? TraceWeaveException)?.let { owned ->
      // Suppressed/custom-field transfer is the owned copier's responsibility (see the interface KDoc).
      return owned.copyWithCause(flying)
    }
    TraceWeave.lookup(flying.javaClass)?.let { copier ->
      // Same contract as the interface: the user/library copier owns suppressed/custom-field transfer.
      return copier(flying)
    }
    BUILTIN_COPIERS[flying.javaClass]?.let { copier ->
      return copier(flying).restoreSuppressedFrom(flying)
    }
    if (TraceWeave.reflectionCopyEnabled()) {
      reflectionCopy(flying)?.let { copy -> return copy.restoreSuppressedFrom(flying) }
    }
    return null
  }

  // Built-in/reflection copies recover only message + cause; restore suppressed (try-with-resources,
  // multi-catch) from the original.
  private fun Throwable.restoreSuppressedFrom(original: Throwable): Throwable =
    apply { original.suppressed.forEach { addSuppressed(it) } }

  // Logs at most one warning per class, so a recurring fallback never floods the log.
  private inline fun warnOnce(
    flying: Throwable,
    message: () -> String,
  ) {
    if (warnedClasses.add(flying.javaClass)) {
      logger.warn(message())
    }
  }

  /** Test-only: drop the per-class warning state accumulated while weaving. */
  internal fun clearState() {
    warnedClasses.clear()
  }
}
