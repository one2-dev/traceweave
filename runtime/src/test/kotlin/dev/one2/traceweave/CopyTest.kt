package dev.one2.traceweave

import dev.one2.traceweave.config.configure
import dev.one2.traceweave.config.resetForTest
import dev.one2.traceweave.constant.Copy
import dev.one2.traceweave.exception.TraceWeaveException
import dev.one2.traceweave.handler.handle
import dev.one2.traceweave.mode.Mode
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
    val result = TestHelper.handleDefault(original)
    assertIs<OrderFailed>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
    assertTrue(result.stackTrace.any { it.className == TestHelper.CLASS && it.methodName == TestHelper.METHOD })
    assertTrue(result.stackTrace.any { it.className == Copy.MARKER })
    assertEquals(originalFrames, original.stackTrace.toList())
  }

  @Test
  fun groupAStandardExceptionIsCopiedThroughConstructor() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException(TestHelper.MESSAGE)
    val result = TestHelper.handleDefault(original)
    assertIs<IllegalStateException>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
  }

  @Test
  fun groupBStandardExceptionIsCopiedThroughInitCause() {
    configure { mode = Mode.COPY }
    val original = NumberFormatException("bad")
    val result = TestHelper.handleDefault(original)
    assertIs<NumberFormatException>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
  }

  @Test
  fun appendingAcrossLevelsKeepsOneCopyAndOneMarker() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException(TestHelper.MESSAGE)
    val inner = handle(original, "C", "inner", "C.kt", 1)
    val outer = handle(inner, "C", "outer", "C.kt", 2)
    assertSame(inner, outer)
    assertSame(original, outer.cause)
    assertEquals(1, outer.stackTrace.count { it.className == Copy.MARKER })
    val names = outer.stackTrace.map { it.methodName }
    assertTrue(names.indexOf("inner") < names.indexOf("outer"))
  }

  @Test
  fun unwritableCopyFallsBackToOriginal() {
    configure { mode = Mode.COPY }
    val original = UnwritableOwned("x")
    val result = TestHelper.handleDefault(original)
    assertSame(original, result)
  }

  @Test
  fun suppressedExceptionsAreCarriedToTheCopy() {
    configure { mode = Mode.COPY }
    val original = IllegalStateException(TestHelper.MESSAGE)
    val suppressed = TestHelper.error("closed badly")
    original.addSuppressed(suppressed)
    val result = TestHelper.handleDefault(original)
    assertTrue(result.suppressed.any { it === suppressed })
  }

  @Test
  fun virtualMachineErrorPassesThroughWithoutCopy() {
    configure { mode = Mode.COPY }
    val error = OutOfMemoryError("heap")
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertFalse(result.stackTrace.any { it.className == Copy.MARKER })
  }

  @Test
  fun unknownTypeWithoutCopierPassesThrough() {
    configure { mode = Mode.COPY }
    val original = CustomUnsupported("nope")
    val result = TestHelper.handleDefault(original)
    assertSame(original, result)
  }

  @Test
  fun realCoroutineCopyCarriesThrowSiteAndChainsOriginal(): Unit =
    runBlocking {
      configure { mode = Mode.COPY }
      val result = requireNotNull(runCatching { tracedOuterCopy() }.exceptionOrNull())
      assertIs<IllegalStateException>(result)
      assertTrue(result.stackTrace.any { it.className == Copy.MARKER })
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
  throw IllegalStateException(TestHelper.MESSAGE)
}

private suspend fun tracedOuterCopy(): Int =
  try {
    failingInnerCopy()
  } catch (e: Throwable) {
    throw handle(e, "Outer", "tracedOuterCopy", "Test.kt", 42)
  }
