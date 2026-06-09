package dev.one2.traceweave.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.mode.Mode
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compiles a snippet in-process with the plugin registered, runs it, and checks whether the synthetic
 * `outer` frame was woven in. `delay(1)` forces a real suspension so the JVM stack is truncated -- the
 * only way `outer` reappears in the trace is if the plugin inserted it.
 */
@OptIn(ExperimentalCompilerApi::class)
class TraceWeavePluginTest {
  @BeforeTest
  fun setUp() {
    configure { mode = Mode.INPLACE }
  }

  @Test
  fun annotatedFunctionWeavesTheCallerFrame() {
    val throwable = run(source(annotated = true))
    assertTrue(throwable.hasFrame("outer"))
  }

  @Test
  fun untracedFunctionIsLeftAlone() {
    val throwable = run(source(annotated = false))
    assertFalse(throwable.hasFrame("outer"))
  }

  @Test
  fun packagePrefixOptIn() {
    val throwable = run(source(annotated = false), prefixes = listOf("sample"))
    assertTrue(throwable.hasFrame("outer"))
  }

  @Test
  fun excludedPrefixWinsOverAnnotation() {
    val throwable = run(source(annotated = true), excluded = listOf("sample"))
    assertFalse(throwable.hasFrame("outer"))
  }

  @Test
  fun nestedTracedFunctionIsNotDoubleWrapped() {
    // In include mode the nested local fun `helper` also matches the prefix, so without the
    // already-woven guard the inner() call would be wrapped twice (by outer and by helper).
    val throwable = run(nestedSource(), prefixes = listOf("sample"))
    assertTrue(throwable.hasFrame("outer")) // woven once, by the outer pass
    assertFalse(throwable.hasFrame("helper")) // ...not a second time by the nested pass
  }

  @Test
  fun summaryReportedAtInfoLevel() {
    // The plugin has no flag of its own: it always reports, and the build's log level gates it. At the
    // default level the one-line summary (INFO -> Gradle `--info`) shows; the per-function detail
    // (LOGGING -> Gradle `--debug`) does not. Rendered prefixes: `i:` for INFO, `v:` for LOGGING.
    val result = compile(source(annotated = true)) // verbose = false == default Gradle log level
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertTrue(result.messages.contains("i: [traceweave]"), result.messages)
    assertFalse(result.messages.contains("v: [traceweave]"), result.messages)
  }

  @Test
  fun perFunctionDetailReportedAtDebugLevel() {
    val result = compile(source(annotated = true), verbose = true) // verbose == Gradle `--debug`
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertTrue(result.messages.contains("v: [traceweave] instrumented"), result.messages)
  }

  private fun source(annotated: Boolean): SourceFile {
    val mark = if (annotated) "@TraceWeave" else ""
    return SourceFile.kotlin(
      "Sample.kt",
      """
      package sample

      import dev.one2.traceweave.annotation.TraceWeave
      import kotlinx.coroutines.delay
      import kotlinx.coroutines.runBlocking

      $mark
      suspend fun inner() {
        delay(1)
        throw RuntimeException("boom")
      }

      $mark
      suspend fun outer() {
        inner()
      }

      fun boom(): Throwable = runBlocking {
        requireNotNull(runCatching { outer() }.exceptionOrNull())
      }
      """.trimIndent(),
    )
  }

  // `verbose` maps to the build's log level: the plugin always reports through the MessageCollector
  // (summary at INFO, per-function at LOGGING), and the compiler's verbosity decides whether it shows --
  // mirroring how Gradle's `--info` / `--debug` gate the same output. No plugin flag of our own.
  private fun compile(
    source: SourceFile,
    prefixes: List<String> = emptyList(),
    excluded: List<String> = emptyList(),
    verbose: Boolean = false,
  ) = KotlinCompilation()
    .apply {
      sources = listOf(source)
      compilerPluginRegistrars = listOf(TraceWeavePluginRegistrar())
      commandLineProcessors = listOf(TraceWeaveCommandLineProcessor())
      pluginOptions =
        buildList {
          prefixes.forEach { add(PluginOption(PLUGIN_ID, "tracedPrefixes", it)) }
          excluded.forEach { add(PluginOption(PLUGIN_ID, "excludedPrefixes", it)) }
        }
      inheritClassPath = true
      messageOutputStream = System.out
      this.verbose = verbose
    }.compile()

  // A traced function whose nested local fun is also traced (include mode): the inner() suspend call
  // sits inside `helper`, which both `outer`'s pass and `helper`'s own pass would otherwise wrap.
  private fun nestedSource(): SourceFile =
    SourceFile.kotlin(
      "Sample.kt",
      """
      package sample

      import kotlinx.coroutines.delay
      import kotlinx.coroutines.runBlocking

      suspend fun inner() {
        delay(1)
        throw RuntimeException("boom")
      }

      suspend fun outer() {
        suspend fun helper() {
          inner()
        }
        helper()
      }

      fun boom(): Throwable = runBlocking {
        requireNotNull(runCatching { outer() }.exceptionOrNull())
      }
      """.trimIndent(),
    )

  private fun run(
    source: SourceFile,
    prefixes: List<String> = emptyList(),
    excluded: List<String> = emptyList(),
  ): Throwable {
    val result = compile(source, prefixes, excluded)
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return result.classLoader
      .loadClass("sample.SampleKt")
      .getMethod("boom")
      .invoke(null) as Throwable
  }

  private fun Throwable.hasFrame(methodName: String): Boolean = stackTrace.any { it.methodName == methodName }

  private companion object {
    const val PLUGIN_ID = "dev.one2.traceweave"
  }
}
