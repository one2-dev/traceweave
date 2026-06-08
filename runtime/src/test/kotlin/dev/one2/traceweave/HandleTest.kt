package dev.one2.traceweave

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HandleTest {
  @BeforeTest
  fun setUp() {
    clearTraceweaveProperties()
    resetForTest()
  }

  @AfterTest
  fun tearDown() {
    clearTraceweaveProperties()
    resetForTest()
  }

  @Test
  fun `inert pass-through when not configured`() {
    val error = RuntimeException("boom")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun `inplace inserts the synthetic frame once configured`() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertTrue(error.stackTrace.any { it.className == "Outer" && it.methodName == "outer" })
  }

  @Test
  fun `activates from a traceweave system property`() {
    System.setProperty(PROP_MODE, Mode.INPLACE.name.lowercase())
    val error = RuntimeException("boom")
    handle(error, "Outer", "outer", "Test.kt", 10)
    assertTrue(error.stackTrace.any { it.methodName == "outer" })
  }

  @Test
  fun `cancellation is passed through even when configured`() {
    configure { mode = Mode.INPLACE }
    val error = CancellationException("cancel")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun `copy mode passes through until implemented`() {
    configure { mode = Mode.COPY }
    val error = RuntimeException("boom")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun `nested frames keep callee-to-caller order`() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    handle(error, "C", "bot", "Test.kt", 1)
    handle(error, "C", "middle", "Test.kt", 2)
    handle(error, "C", "top", "Test.kt", 3)
    val names = error.stackTrace.map { it.methodName }
    val bot = names.indexOf("bot")
    val middle = names.indexOf("middle")
    val top = names.indexOf("top")
    assertTrue(bot in 0 until middle && middle < top)
  }

  @Test
  fun `real coroutine with manual instrumentation reconstructs the caller frame`() =
    runBlocking {
      configure { mode = Mode.INPLACE }
      val error = runCatching { tracedOuter() }.exceptionOrNull()!!
      assertTrue(error.stackTrace.any { it.className == "Outer" && it.methodName == "tracedOuter" })
    }

  private fun clearTraceweaveProperties() {
    val names = System.getProperties().stringPropertyNames().filter { it.startsWith(PROP_PREFIX) }
    names.forEach { System.clearProperty(it) }
  }
}

// Mimics what the plugin generates: a real suspend chain whose JVM stack is truncated at the
// suspension point, with a hand-written catch that routes through handle(). No compiler plugin.
private suspend fun failingInner(): Int {
  delay(1)
  throw IllegalStateException("boom")
}

private suspend fun tracedOuter(): Int =
  try {
    failingInner()
  } catch (e: Throwable) {
    throw handle(e, "Outer", "tracedOuter", "Test.kt", 42)
  }
