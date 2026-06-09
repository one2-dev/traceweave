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

  private fun run(
    source: SourceFile,
    prefixes: List<String> = emptyList(),
    excluded: List<String> = emptyList(),
  ): Throwable {
    val options =
      buildList {
        prefixes.forEach {
          add(PluginOption(PLUGIN_ID, "tracedPrefixes", it))
        }
        excluded.forEach {
          add(PluginOption(PLUGIN_ID, "excludedPrefixes", it))
        }
      }
    val result =
      KotlinCompilation()
        .apply {
          sources = listOf(source)
          compilerPluginRegistrars = listOf(TraceWeavePluginRegistrar())
          commandLineProcessors = listOf(TraceWeaveCommandLineProcessor())
          pluginOptions = options
          inheritClassPath = true
          messageOutputStream = System.out
        }.compile()
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
