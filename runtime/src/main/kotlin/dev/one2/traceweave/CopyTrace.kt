package dev.one2.traceweave

import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import java.util.Collections
import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeoutException

// Sentinel placed in a synthetic frame's declaringClass. It is not a valid Java identifier, so no
// real class name collides with it -- which is exactly why membership ("is this already our copy?")
// can live in the stack trace itself instead of a global set.
internal const val MARKER = "--- @TraceWeave ---"
private val MARKER_FRAME = StackTraceElement(MARKER, "", null, -1)

private const val EMPTY_COPY_WARNING =
  "traceweave: copy of %s came back without a writable stack trace; using the original instead"

// Frames at or below these belong to the coroutine machinery, not user code. The seed (the
// throw-site head we copy onto a fresh copy) stops at the first such frame.
private const val COROUTINES_PACKAGE = "kotlinx.coroutines."
private const val CONTINUATION_CLASS = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
private const val SUSPEND_METHOD = "invokeSuspend"

// One warning per offending class, so a misbehaving copier cannot flood the log.
private val warnedEmptyCopy: MutableSet<Class<*>> = Collections.synchronizedSet(HashSet())

/**
 * COPY-mode counterpart of [insertInplaceFrame]. Writes the synthetic frame onto a fresh copy of the
 * same type (with the original chained as cause) and returns it, leaving the original untouched. On
 * the next unwind level the copy is recognised by its [MARKER] frame and the new frame is appended,
 * so a whole chain produces exactly one copy. Falls back to the original whenever a copy cannot be
 * made (no copier, or the copier yields an unwritable instance).
 */
internal fun insertCopyFrame(
  flying: Throwable,
  declaringClass: String,
  methodName: String,
  fileName: String,
  lineNumber: Int,
): Throwable {
  val frame = StackTraceElement(declaringClass, methodName, fileName, lineNumber)
  if (flying.isOurs()) {
    flying.stackTrace += frame
    return flying
  }
  val copy = tryCopy(flying) ?: return flying
  copy.stackTrace = seed(flying) + MARKER_FRAME + frame
  if (copy.stackTrace.isEmpty()) {
    // An unwritable copy (writableStackTrace = false) silently drops the assignment above.
    warnOnceEmptyCopy(flying)
    return flying
  }
  return copy
}

private fun Throwable.isOurs(): Boolean = stackTrace.any { it.className == MARKER }

// The real throw-site head of [flying], up to (but not including) the first coroutine-machinery
// frame, so the copy's own trace points at where it was thrown; the full original lives in the cause.
private fun seed(flying: Throwable): Array<StackTraceElement> {
  val st = flying.stackTrace
  val cut = st.indexOfFirst { it.isCoroutineMachinery() }
  return if (cut < 0) st else st.copyOfRange(0, cut)
}

private fun StackTraceElement.isCoroutineMachinery(): Boolean =
  className.startsWith(COROUTINES_PACKAGE) ||
    className == CONTINUATION_CLASS ||
    methodName == SUSPEND_METHOD

// Resolution order: owned interface -> built-in table -> null (registerCopier and reflection slot in
// here in later commits). Any copier failure degrades to null so handle() returns the original.
private fun tryCopy(flying: Throwable): Throwable? {
  (flying as? TraceWeaveException)?.let { owned ->
    // Suppressed/custom-field transfer is the owned copier's responsibility (see the interface KDoc).
    return runCatching { owned.copyWithCause(flying) }.getOrNull()
  }
  val copier = BUILTIN_COPIERS[flying.javaClass] ?: return null
  val copy = runCatching { copier(flying) }.getOrNull() ?: return null
  // Table copies recover only message + cause; restore suppressed (try-with-resources, multi-catch).
  flying.suppressed.forEach { copy.addSuppressed(it) }
  return copy
}

private fun warnOnceEmptyCopy(flying: Throwable) {
  if (warnedEmptyCopy.add(flying.javaClass)) {
    logger.warn(EMPTY_COPY_WARNING.format(flying.javaClass.name))
  }
}

internal fun clearCopyState() {
  warnedEmptyCopy.clear()
}

// Sets [cause] on a freshly built throwable whose cause is still uninitialised, returning it. The
// group-B path for types that only expose a ()/(String) constructor.
private fun Throwable.withCause(cause: Throwable): Throwable = apply { initCause(cause) }

/**
 * Reflection-free copiers for common JDK/Kotlin exceptions, keyed by exact class. Each rebuilds the
 * same type preserving `message` and chaining the original as cause -- group A through a
 * `(message, cause)` constructor, group B by constructing then [withCause]. `CancellationException`
 * and `VirtualMachineError` never reach here (passed through earlier); `UncheckedIOException` is
 * absent because its constructor demands an `IOException` cause we cannot supply.
 */
private val BUILTIN_COPIERS: Map<Class<*>, (Throwable) -> Throwable> =
  mapOf(
    // Group A -- cause via constructor.
    Throwable::class.java to { o -> Throwable(o.message, o) },
    Exception::class.java to { o -> Exception(o.message, o) },
    RuntimeException::class.java to { o -> RuntimeException(o.message, o) },
    Error::class.java to { o -> Error(o.message, o) },
    IllegalArgumentException::class.java to { o -> IllegalArgumentException(o.message, o) },
    IllegalStateException::class.java to { o -> IllegalStateException(o.message, o) },
    UnsupportedOperationException::class.java to { o -> UnsupportedOperationException(o.message, o) },
    SecurityException::class.java to { o -> SecurityException(o.message, o) },
    ReflectiveOperationException::class.java to { o -> ReflectiveOperationException(o.message, o) },
    ClassNotFoundException::class.java to { o -> ClassNotFoundException(o.message, o) },
    IOException::class.java to { o -> IOException(o.message, o) },
    ConcurrentModificationException::class.java to { o -> ConcurrentModificationException(o.message, o) },
    ExecutionException::class.java to { o -> ExecutionException(o.message, o) },
    CompletionException::class.java to { o -> CompletionException(o.message, o) },
    RejectedExecutionException::class.java to { o -> RejectedExecutionException(o.message, o) },
    UninitializedPropertyAccessException::class.java to { o -> UninitializedPropertyAccessException(o.message, o) },
    // Special -- cause-first constructors.
    InvocationTargetException::class.java to { o -> InvocationTargetException(o, o.message) },
    UndeclaredThrowableException::class.java to { o -> UndeclaredThrowableException(o, o.message) },
    // Group B -- cause via initCause on a fresh instance.
    NullPointerException::class.java to { o -> NullPointerException(o.message).withCause(o) },
    IndexOutOfBoundsException::class.java to { o -> IndexOutOfBoundsException(o.message).withCause(o) },
    ArrayIndexOutOfBoundsException::class.java to { o -> ArrayIndexOutOfBoundsException(o.message).withCause(o) },
    StringIndexOutOfBoundsException::class.java to { o -> StringIndexOutOfBoundsException(o.message).withCause(o) },
    NumberFormatException::class.java to { o -> NumberFormatException(o.message).withCause(o) },
    ArithmeticException::class.java to { o -> ArithmeticException(o.message).withCause(o) },
    ClassCastException::class.java to { o -> ClassCastException(o.message).withCause(o) },
    NegativeArraySizeException::class.java to { o -> NegativeArraySizeException(o.message).withCause(o) },
    ArrayStoreException::class.java to { o -> ArrayStoreException(o.message).withCause(o) },
    InterruptedException::class.java to { o -> InterruptedException(o.message).withCause(o) },
    CloneNotSupportedException::class.java to { o -> CloneNotSupportedException(o.message).withCause(o) },
    NoSuchElementException::class.java to { o -> NoSuchElementException(o.message).withCause(o) },
    FileNotFoundException::class.java to { o -> FileNotFoundException(o.message).withCause(o) },
    EOFException::class.java to { o -> EOFException(o.message).withCause(o) },
    TimeoutException::class.java to { o -> TimeoutException(o.message).withCause(o) },
    NotImplementedError::class.java to { o -> NotImplementedError(o.message ?: "").withCause(o) },
    TypeCastException::class.java to { o -> TypeCastException(o.message).withCause(o) },
  )
