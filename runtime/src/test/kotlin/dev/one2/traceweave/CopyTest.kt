package dev.one2.traceweave

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CopyTest {
  @BeforeTest
  fun setUp() {
    resetForTest()
  }

  @AfterTest
  fun tearDown() {
    resetForTest()
  }

  @Test
  fun ownedTypeIsCopiedViaInterface() {
    configure { mode = Mode.COPY }
    val original = OrderFailed("nope")
    val originalFrames = original.stackTrace.toList()
    val result = handle(original, "Svc", "place", "Svc.kt", 7)
    assertIs<OrderFailed>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
    assertTrue(result.stackTrace.any { it.className == "Svc" && it.methodName == "place" })
    assertTrue(result.stackTrace.any { it.className == MARKER })
    assertEquals(originalFrames, original.stackTrace.toList())
  }

  @Test
  fun groupAStandardExceptionIsCopiedThroughConstructor() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException("boom")
    val result = handle(original, "Svc", "m", "Svc.kt", 1)
    assertIs<IllegalStateException>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
  }

  @Test
  fun groupBStandardExceptionIsCopiedThroughInitCause() {
    configure { mode = Mode.COPY }
    val original = NumberFormatException("bad")
    val result = handle(original, "Svc", "m", "Svc.kt", 1)
    assertIs<NumberFormatException>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
  }

  @Test
  fun appendingAcrossLevelsKeepsOneCopyAndOneMarker() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException("boom")
    val inner = handle(original, "C", "inner", "C.kt", 1)
    val outer = handle(inner, "C", "outer", "C.kt", 2)
    assertSame(inner, outer)
    assertSame(original, outer.cause)
    assertEquals(1, outer.stackTrace.count { it.className == MARKER })
    val names = outer.stackTrace.map { it.methodName }
    assertTrue(names.indexOf("inner") < names.indexOf("outer"))
  }

  @Test
  fun unwritableCopyFallsBackToOriginal() {
    configure { mode = Mode.COPY }
    val original = UnwritableOwned("x")
    val result = handle(original, "C", "m", "C.kt", 1)
    assertSame(original, result)
  }

  @Test
  fun suppressedExceptionsAreCarriedToTheCopy() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException("boom")
    val suppressed = RuntimeException("closed badly")
    original.addSuppressed(suppressed)
    val result = handle(original, "C", "m", "C.kt", 1)
    assertTrue(result.suppressed.any { it === suppressed })
  }

  @Test
  fun virtualMachineErrorPassesThroughWithoutCopy() {
    configure { mode = Mode.COPY }
    val error = OutOfMemoryError("heap")
    val result = handle(error, "C", "m", "C.kt", 1)
    assertSame(error, result)
    assertFalse(result.stackTrace.any { it.className == MARKER })
  }

  @Test
  fun unknownTypeWithoutCopierPassesThrough() {
    configure { mode = Mode.COPY }
    val original = CustomUnsupported("nope")
    val result = handle(original, "C", "m", "C.kt", 1)
    assertSame(original, result)
  }

  @Test
  fun realCoroutineCopyCarriesThrowSiteAndChainsOriginal() =
    runBlocking {
      configure { mode = Mode.COPY }
      val result = runCatching { tracedOuterCopy() }.exceptionOrNull()!!
      assertIs<IllegalStateException>(result)
      assertTrue(result.stackTrace.any { it.className == MARKER })
      assertTrue(result.stackTrace.any { it.methodName == "tracedOuterCopy" })
      assertNotNull(result.cause)
    }
}

private class OrderFailed(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause), TraceWeaveException {
  override fun copyWithCause(cause: Throwable): Throwable = OrderFailed(message ?: "", cause)
}

// copyWithCause hands back an instance built with writableStackTrace = false, so traceweave cannot
// stamp frames onto it -- the empty-copy guard must kick in and the original is used.
private class UnwritableOwned(message: String) : RuntimeException(message), TraceWeaveException {
  override fun copyWithCause(cause: Throwable): Throwable = object : RuntimeException(message, cause, true, false) {}
}

// No built-in copier and not TraceWeaveException -> COPY must pass it through untouched.
private class CustomUnsupported(message: String) : RuntimeException(message)

private suspend fun failingInnerCopy(): Int {
  delay(1)
  throw IllegalStateException("boom")
}

private suspend fun tracedOuterCopy(): Int =
  try {
    failingInnerCopy()
  } catch (e: Throwable) {
    throw handle(e, "Outer", "tracedOuterCopy", "Test.kt", 42)
  }
