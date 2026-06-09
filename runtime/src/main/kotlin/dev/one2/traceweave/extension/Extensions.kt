package dev.one2.traceweave.extension

import dev.one2.traceweave.constant.Copy

// "Is this already one of our copies?" Membership lives on the exception itself via the marker frame.
fun Throwable.wasCopied(): Boolean = stackTrace.any { it.className == Copy.MARKER }

// True for frames that belong to the coroutine machinery rather than user code.
fun StackTraceElement.isCoroutineMachinery(): Boolean =
  className.startsWith(Copy.COROUTINE_PACKAGE) ||
    className == Copy.CONTINUATION_CLASS ||
    methodName == Copy.SUSPEND_METHOD

// Sets the cause on a freshly built throwable whose cause is still uninitialised, returning it. Used
// by the group-B built-in copiers for types that only expose a ()/(String) constructor.
fun Throwable.withCause(cause: Throwable): Throwable = apply { initCause(cause) }
