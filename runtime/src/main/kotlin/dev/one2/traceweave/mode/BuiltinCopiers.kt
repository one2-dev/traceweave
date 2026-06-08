package dev.one2.traceweave.mode

import dev.one2.traceweave.extension.withCause
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import java.util.ConcurrentModificationException
import java.util.NoSuchElementException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeoutException

/**
 * Reflection-free copiers for common JDK/Kotlin exceptions, keyed by exact class. Each rebuilds the
 * same type preserving `message` and chaining the original as cause -- group A through a
 * `(message, cause)` constructor, group B by constructing then [withCause]. `CancellationException`
 * and `VirtualMachineError` never reach here (passed through earlier); `UncheckedIOException` is
 * absent because its constructor demands an `IOException` cause we cannot supply.
 */
internal val BUILTIN_COPIERS: Map<Class<*>, (Throwable) -> Throwable> =
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
