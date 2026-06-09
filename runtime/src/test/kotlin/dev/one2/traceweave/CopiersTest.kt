package dev.one2.traceweave

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.TraceWeave.register
import dev.one2.traceweave.TraceWeave.reset
import dev.one2.traceweave.constant.Configuration
import dev.one2.traceweave.constant.Copy
import dev.one2.traceweave.mode.Mode
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the copier registry (commit 5): [TraceWeave.register] for 3rd-party types, ServiceLoader
 * provider discovery, the app-overrides-provider priority, the registry's slot between the owned
 * interface and the built-in table, and the inertness guarantee (registering never activates the
 * runtime or touches policy).
 */
class CopiersTest {
  @BeforeTest
  fun setUp() {
    reset()
  }

  @AfterTest
  fun tearDown() {
    reset()
  }

  @Test
  fun registeredCopierReconstructsUnownedType() {
    configure { mode = Mode.COPY }
    register<ThirdPartyException> { original -> ThirdPartyException(original.message, original) }
    val original = ThirdPartyException("nope")
    val result = TestHelper.handleDefault(original)
    assertIs<ThirdPartyException>(result)
    assertNotSame(original, result)
    assertSame(original, result.cause)
    assertTrue(result.stackTrace.any { it.className == Copy.MARKER })
  }

  @Test
  fun unregisteredUnownedTypePassesThrough() {
    configure { mode = Mode.COPY }
    val original = ThirdPartyException("nope")
    assertSame(original, TestHelper.handleDefault(original))
  }

  @Test
  fun registeredCopierWinsOverBuiltinTable() {
    configure { mode = Mode.COPY }
    register<IllegalStateException> { original -> IllegalStateException("custom:${original.message}", original) }
    val original = IllegalStateException("boom")
    val result = TestHelper.handleDefault(original)
    assertIs<IllegalStateException>(result)
    assertNotSame(original, result)
    assertEquals("custom:boom", result.message)
    assertSame(original, result.cause)
  }

  @Test
  fun serviceLoaderProviderIsPickedUp() {
    configure { mode = Mode.COPY }
    val original = ProviderSuppliedException("hi")
    val result = TestHelper.handleDefault(original)
    assertIs<ProviderSuppliedException>(result)
    assertEquals("provider", result.copiedBy)
    assertSame(original, result.cause)
  }

  @Test
  fun explicitRegistrationOverridesProvider() {
    configure { mode = Mode.COPY }
    register<ProviderSuppliedException> { original ->
      ProviderSuppliedException(original.message, copiedBy = "app", cause = original)
    }
    val result = TestHelper.handleDefault(ProviderSuppliedException("hi"))
    assertIs<ProviderSuppliedException>(result)
    assertEquals("app", result.copiedBy)
  }

  @Test
  fun registeringCopierDoesNotActivateTraceweave() {
    // No configure() and no property: registering a copier must leave traceweave inert (a library
    // contributing copiers cannot flip the runtime on or touch application policy).
    register<ThirdPartyException> { original -> ThirdPartyException(original.message, original) }
    val original = ThirdPartyException("nope")
    assertSame(original, TestHelper.handleDefault(original))
  }

  @Test
  fun duplicateProviderForSameTypeIsWarned() {
    val logger = LoggerFactory.getLogger(Configuration.LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().also { it.start() }
    logger.addAppender(appender)
    try {
      configure { mode = Mode.COPY }
      // Triggers lazy provider discovery; ProviderA and ProviderB both claim ProviderSuppliedException.
      TestHelper.handleDefault(ProviderSuppliedException("hi"))
      assertTrue(
        appender.list.any { it.formattedMessage.contains(ProviderSuppliedException::class.java.name) },
      )
    } finally {
      logger.detachAppender(appender)
    }
  }
}
