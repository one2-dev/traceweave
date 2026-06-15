package dev.one2.traceweave

import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.TraceWeave.configureInPlace
import dev.one2.traceweave.TraceWeave.handle
import dev.one2.traceweave.TraceWeave.reset
import dev.one2.traceweave.constant.Configuration
import dev.one2.traceweave.mode.Mode
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
    reset()
  }

  @AfterTest
  fun tearDown() {
    clearTraceweaveProperties()
    reset()
  }

  @Test
  fun inertPassThroughWhenNotConfigured() {
    val error = TestHelper.error()
    val before = error.stackTrace.toList()
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun inplaceInsertsFrameOnceConfigured() {
    configure { mode = Mode.INPLACE }
    val error = TestHelper.error()
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertTrue(error.stackTrace.any { it.className == TestHelper.CLASS && it.methodName == TestHelper.METHOD })
  }

  @Test
  fun configureInPlaceActivatesInplaceWithDefaults() {
    configureInPlace()
    val error = TestHelper.error()
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertTrue(error.stackTrace.any { it.className == TestHelper.CLASS && it.methodName == TestHelper.METHOD })
  }

  @Test
  fun activatesFromSystemProperty() {
    System.setProperty(Configuration.PROP_ENABLED, true.toString())
    System.setProperty(Configuration.PROP_MODE, Mode.INPLACE.name.lowercase())
    val error = TestHelper.error()
    TestHelper.handleDefault(error)
    assertTrue(error.stackTrace.any { it.methodName == TestHelper.METHOD })
  }

  @Test
  fun cancellationIsPassedThrough() {
    configure { mode = Mode.INPLACE }
    val error = CancellationException("cancel")
    val before = error.stackTrace.toList()
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertEquals(before, error.stackTrace.toList())
  }

  @Test
  fun nestedFramesKeepCalleeToCallerOrder() {
    configure { mode = Mode.INPLACE }
    val error = TestHelper.error()
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
    val error = TestHelper.error()
    repeat(5) { handle(error, "C", "loop", "Test.kt", 7) }
    val count = error.stackTrace.count { it.className == "C" && it.methodName == "loop" }
    assertEquals(1, count)
  }

  @Test
  fun frameFromAnotherSourceIsNotDuplicated() {
    configure { mode = Mode.INPLACE }
    val error = TestHelper.error()
    // Simulate a frame already inserted by coroutine recovery / DebugProbes near the top.
    val existing = StackTraceElement(TestHelper.CLASS, TestHelper.METHOD, TestHelper.FILE, TestHelper.LINE)
    val original = error.stackTrace
    error.stackTrace = arrayOf(original.first(), existing) + original.drop(1)
    TestHelper.handleDefault(error)
    val count = error.stackTrace.count { it.className == TestHelper.CLASS && it.methodName == TestHelper.METHOD }
    assertEquals(1, count)
  }

  @Test
  fun concurrentHandleLosesNoDistinctFrame() {
    configure { mode = Mode.INPLACE }
    val error = TestHelper.error()
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
      val error = requireNotNull(runCatching { tracedOuter() }.exceptionOrNull())
      assertTrue(error.stackTrace.any { it.className == "Outer" && it.methodName == "tracedOuter" })
    }

  private fun clearTraceweaveProperties() {
    val names = System.getProperties().stringPropertyNames().filter { it.startsWith(Configuration.PROP_PREFIX) }
    names.forEach { System.clearProperty(it) }
  }
}

// Mimics what the plugin generates: a real suspend chain whose JVM stack is truncated at the
// suspension point, with a hand-written catch that routes through handle(). No compiler plugin.
private suspend fun failingInner(): Int {
  delay(1)
  throw IllegalStateException(TestHelper.MESSAGE)
}

private suspend fun tracedOuter(): Int =
  try {
    failingInner()
  } catch (e: Throwable) {
    throw handle(e, "Outer", "tracedOuter", "Test.kt", 42)
  }
