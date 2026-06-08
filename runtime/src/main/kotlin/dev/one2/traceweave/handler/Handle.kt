package dev.one2.traceweave.handler

import dev.one2.traceweave.config.activeConfig
import dev.one2.traceweave.constant.Message
import dev.one2.traceweave.logging.logger
import dev.one2.traceweave.mode.Mode
import dev.one2.traceweave.mode.insertCopyFrame
import dev.one2.traceweave.mode.insertInplaceFrame
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runtime entry point called by plugin-generated code in the catch block around a suspend call:
 * `catch (e: Throwable) { throw handle(e, "com.x.C", "m", "C.kt", 42) }`.
 *
 * Returns the exception to actually rethrow: the same instance in [Mode.INPLACE], a fresh copy of the
 * same type (original chained as its cause) in [Mode.COPY].
 *
 * Until traceweave is configured (via `configure` or a `traceweave.*` property) this is a no-op
 * pass-through: the original exception is returned untouched. [CancellationException] and
 * [VirtualMachineError] are always passed through (the latter because allocating a copy or array
 * while the VM is already failing only makes things worse). Best-effort: any failure inside leaves the
 * original exception unchanged and is reported through the slf4j logger rather than propagated.
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
    when (config.mode) {
      Mode.INPLACE -> {
        insertInplaceFrame(error, declaringClass, methodName, fileName, lineNumber)
        error
      }
      Mode.COPY -> insertCopyFrame(error, declaringClass, methodName, fileName, lineNumber)
    }
  } catch (t: Throwable) {
    logger.warn(Message.FRAME_INSERT_FAILED, t)
    error
  }
}
