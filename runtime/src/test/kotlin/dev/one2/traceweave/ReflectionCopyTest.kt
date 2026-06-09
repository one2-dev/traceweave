package dev.one2.traceweave

import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.TraceWeave.reset
import dev.one2.traceweave.constant.Copy
import dev.one2.traceweave.mode.Mode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the reflection copier (commit 6): the opt-in last resolution layer in COPY mode that rebuilds
 * an unowned type via its constructors. Off by default (pass-through); on via `reflectionCopy`.
 */
class ReflectionCopyTest {
  @BeforeTest
  fun setUp() {
    reset()
  }

  @AfterTest
  fun tearDown() {
    reset()
  }

  @Test
  fun reflectionCopiesUnownedTypeWhenEnabled() {
    configure {
      mode = Mode.COPY
      reflectionCopy = true
    }
    // ThirdPartyException is not TraceWeaveException, not registered, and not in the built-in table, so
    // only reflection (via its (String, Throwable) constructor) can rebuild it.
    val original = ThirdPartyException("nope")
    val result = TestHelper.handleDefault(original)
    assertIs<ThirdPartyException>(result)
    assertNotSame(original, result)
    assertEquals("nope", result.message)
    assertSame(original, result.cause)
    assertTrue(result.stackTrace.any { it.className == Copy.MARKER })
  }

  @Test
  fun unownedTypePassesThroughWhenDisabled() {
    configure { mode = Mode.COPY } // reflectionCopy defaults to false
    val original = ThirdPartyException("nope")
    assertSame(original, TestHelper.handleDefault(original))
  }

  @Test
  fun typeWithoutUsableConstructorPassesThroughEvenWhenEnabled() {
    configure {
      mode = Mode.COPY
      reflectionCopy = true
    }
    // Only an (Int) constructor -- none of the reflection-probed Throwable shapes -- so reflection bails.
    val original = NoStandardCtorException(42)
    assertSame(original, TestHelper.handleDefault(original))
  }
}

private class NoStandardCtorException(code: Int) : RuntimeException("code=$code")
