package dev.one2.traceweave

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.TraceWeave.handle
import dev.one2.traceweave.TraceWeave.reset
import dev.one2.traceweave.constant.Configuration
import dev.one2.traceweave.mode.Mode
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies the diagnostics that [handle] logs on its best-effort catch path.
 *
 * How the capture works: the runtime logs through the slf4j facade to a logger named
 * [Configuration.LOGGER_NAME]. slf4j only routes calls -- the real binding on the test classpath is
 * logback-classic (a `testImplementation` dependency), so `LoggerFactory.getLogger()` actually hands
 * back a logback [Logger]. Loggers are cached by name, so this is the very same instance the runtime
 * writes to. We attach a logback [ListAppender] to it; the appender records every logging event into
 * an in-memory list ([ListAppender.list]), which lets the tests assert what was logged instead of
 * scraping the console.
 */
class LoggerTest {
  private lateinit var logger: Logger
  private lateinit var appender: ListAppender<ILoggingEvent>

  @BeforeTest
  fun setUp() {
    reset()
    // Same name as the runtime's logger -> same instance; cast is safe because logback is the binding.
    logger = LoggerFactory.getLogger(Configuration.LOGGER_NAME) as Logger
    // A started ListAppender collects events into its `list`; attach it so we see what handle() logs.
    appender = ListAppender<ILoggingEvent>().also { it.start() }
    logger.addAppender(appender)
  }

  @AfterTest
  fun tearDown() {
    logger.detachAppender(appender)
    reset()
  }

  @Test
  fun swallowedFailureIsLogged() {
    configure { mode = Mode.INPLACE }
    val error = HostileException()
    val result = TestHelper.handleDefault(error)
    assertSame(error, result)
    assertEquals(1, appender.list.size)
    assertNotNull(appender.list.single().throwableProxy)
  }

  @Test
  fun successPathDoesNotLog() {
    configure { mode = Mode.INPLACE }
    TestHelper.handleDefault(TestHelper.error("ok"))
    assertTrue(appender.list.isEmpty())
  }
}

// A throwable that refuses stack-trace mutation, forcing handle() down its best-effort catch path.
private class HostileException : RuntimeException("hostile") {
  override fun setStackTrace(stackTrace: Array<StackTraceElement>): Unit =
    throw IllegalStateException("stack trace is immutable here")
}
