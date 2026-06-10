package dev.one2.traceweave

import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.TraceWeave.handle
import dev.one2.traceweave.TraceWeave.reset
import dev.one2.traceweave.constant.Copy
import dev.one2.traceweave.exception.TraceWeaveException
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
    reset()
  }

  @AfterTest
  fun tearDown() {
    reset()
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
    assertEquals(1, outer.stackTrace.count { it.className == Copy.MARKER && it.fileName == Copy.WEAVE_LABEL })
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

  @Test
  fun reconstructionAfterMarkerLeadsWithTheThrowLeaf(): Unit =
    runBlocking {
      configure { mode = Mode.COPY }
      val result = requireNotNull(runCatching { tracedOuterCopy() }.exceptionOrNull())
      val marker = result.stackTrace.indexOfFirst { it.className == Copy.MARKER && it.fileName == Copy.WEAVE_LABEL }
      val afterMarker = result.stackTrace.drop(marker + 1).map { it.methodName }
      // The reconstruction (everything after the marker) leads with the real throw leaf, then the caller.
      assertEquals("failingInnerCopy", afterMarker.first())
      assertTrue(afterMarker.indexOf("failingInnerCopy") < afterMarker.indexOf("tracedOuterCopy"))
    }

  @Test
  fun seedIsLabelledFromCauseAndCappedAtTheConfiguredDepth() {
    configure {
      mode = Mode.COPY
      copySeedFrames = 1
    }
    val original = IllegalStateException(TestHelper.MESSAGE)
    val result = TestHelper.handleDefault(original)
    val cause = result.stackTrace.indexOfFirst { it.className == Copy.MARKER && it.fileName == Copy.SEED_LABEL }
    val marker = result.stackTrace.indexOfFirst { it.className == Copy.MARKER && it.fileName == Copy.WEAVE_LABEL }
    // The cause sentinel heads the seed, then exactly one seed frame, then the reconstruction marker.
    assertTrue(cause in 0 until marker)
    assertEquals(1, marker - cause - 1)
  }

  @Test
  fun zeroSeedFramesStartTheCopyAtTheMarkerWithoutACauseLabel() {
    configure {
      mode = Mode.COPY
      copySeedFrames = 0
    }
    val original = IllegalStateException(TestHelper.MESSAGE)
    val result = TestHelper.handleDefault(original)
    // With no seed there must be exactly one marker overall: the reconstruction marker, no cause label.
    assertEquals(0, result.stackTrace.count { it.className == Copy.MARKER && it.fileName == Copy.SEED_LABEL })
    assertEquals(1, result.stackTrace.count { it.className == Copy.MARKER && it.fileName == Copy.WEAVE_LABEL })
    assertEquals(Copy.MARKER, result.stackTrace.first().className)
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
