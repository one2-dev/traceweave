package dev.one2.traceweave

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
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
  fun inertPassThroughWhenNotConfigured() {
    val error = RuntimeException("boom")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun inplaceInsertsFrameOnceConfigured() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertTrue(error.stackTrace.any { it.className == "Outer" && it.methodName == "outer" })
  }

  @Test
  fun activatesFromSystemProperty() {
    System.setProperty(PROP_MODE, Mode.INPLACE.name.lowercase())
    val error = RuntimeException("boom")
    handle(error, "Outer", "outer", "Test.kt", 10)
    assertTrue(error.stackTrace.any { it.methodName == "outer" })
  }

  @Test
  fun cancellationIsPassedThrough() {
    configure { mode = Mode.INPLACE }
    val error = CancellationException("cancel")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun copyModePassesThroughUntilImplemented() {
    configure { mode = Mode.COPY }
    val error = RuntimeException("boom")
    val before = error.stackTrace.toList()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun nestedFramesKeepCalleeToCallerOrder() {
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
  fun repeatedIdenticalFrameCollapses() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    repeat(5) { handle(error, "C", "loop", "Test.kt", 7) }
    val count = error.stackTrace.count { it.className == "C" && it.methodName == "loop" }
    assertEquals(1, count)
  }

  @Test
  fun frameFromAnotherSourceIsNotDuplicated() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    // Simulate a frame already inserted by coroutine recovery / DebugProbes near the top.
    val existing = StackTraceElement("Outer", "outer", "Test.kt", 10)
    val original = error.stackTrace
    error.stackTrace = arrayOf(original.first(), existing) + original.drop(1)
    handle(error, "Outer", "outer", "Test.kt", 10)
    val count = error.stackTrace.count { it.className == "Outer" && it.methodName == "outer" }
    assertEquals(1, count)
  }

  @Test
  fun concurrentHandleLosesNoDistinctFrame() {
    configure { mode = Mode.INPLACE }
    val error = RuntimeException("boom")
    val threads = 16
    val barrier = CyclicBarrier(threads)
    val pool = Executors.newFixedThreadPool(threads)
    try {
      val futures =
        (0 until threads).map { i ->
          pool.submit {
            barrier.await()
            handle(error, "C", "m$i", "Test.kt", i)
          }
        }
      futures.forEach { it.get() }
    } finally {
      pool.shutdownNow()
    }
    val present = (0 until threads).count { i -> error.stackTrace.any { it.methodName == "m$i" } }
    assertEquals(threads, present)
  }

  @Test
  fun realCoroutineReconstructsCallerFrame() =
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
