package dev.one2.traceweave

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoggerTest {
  private lateinit var logger: Logger
  private lateinit var appender: ListAppender<ILoggingEvent>

  @BeforeTest
  fun setUp() {
    resetForTest()
    logger = LoggerFactory.getLogger(LOGGER_NAME) as Logger
    appender = ListAppender<ILoggingEvent>().also { it.start() }
    logger.addAppender(appender)
  }

  @AfterTest
  fun tearDown() {
    logger.detachAppender(appender)
    resetForTest()
  }

  @Test
  fun swallowedFailureIsLogged() {
    configure { mode = Mode.INPLACE }
    val error = HostileException()
    val result = handle(error, "Outer", "outer", "Test.kt", 10)
    assertSame(error, result)
    assertEquals(1, appender.list.size)
    assertNotNull(appender.list.single().throwableProxy)
  }

  @Test
  fun successPathDoesNotLog() {
    configure { mode = Mode.INPLACE }
    handle(RuntimeException("ok"), "Outer", "outer", "Test.kt", 10)
    assertTrue(appender.list.isEmpty())
  }
}

// A throwable that refuses stack-trace mutation, forcing handle() down its best-effort catch path.
private class HostileException : RuntimeException("hostile") {
  override fun setStackTrace(stackTrace: Array<StackTraceElement>) {
    throw IllegalStateException("stack trace is immutable here")
  }
}
