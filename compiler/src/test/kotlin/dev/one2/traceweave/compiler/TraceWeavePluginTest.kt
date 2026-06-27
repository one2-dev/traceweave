package dev.one2.traceweave.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import dev.one2.traceweave.TraceWeave.configure
import dev.one2.traceweave.mode.Mode
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
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

  @Test
  fun wrappedCallKeepsTheOriginalSourceOffsets() {
    val records = mutableListOf<OffsetRecord>()
    val result = compile(source(annotated = true), extraRegistrars = listOf(CaptureRegistrar(records)))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    // The plugin wrapped at least one suspend call (e.g. outer() -> inner()).
    assertTrue(records.isNotEmpty(), "no wrapped suspend call was captured")
    records.forEach { r ->
      // Real source positions, not UNDEFINED_OFFSET (-1) / SYNTHETIC_OFFSET (-2).
      assertTrue(r.callStart >= 0 && r.callEnd >= 0, "wrapped call lost its source offsets: $r")
      // The synthetic try carries the wrapped call's exact offsets, so the line-number table stays
      // aligned -- breakpoints on the call line and step-into keep working.
      assertEquals(r.callStart, r.tryStart, "try startOffset diverged from the wrapped call: $r")
      assertEquals(r.callEnd, r.tryEnd, "try endOffset diverged from the wrapped call: $r")
    }
  }

  @Test
  fun synthesizedInterfaceDelegateIsNotInstrumented() {
    // `class CachingRepo(...) : Repo by delegate` makes the compiler synthesize a `load` member that
    // just forwards to `delegate.load(id)`. That synthetic member has DELEGATED_MEMBER origin and
    // synthetic source offsets -- wrapping its forwarding suspend call breaks codegen. The plugin must
    // leave it alone.
    val records = mutableListOf<OffsetRecord>()
    val result = compile(delegationSource(), extraRegistrars = listOf(CaptureRegistrar(records)))
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    assertTrue(records.isEmpty(), "the synthetic delegated suspend call was wrapped: $records")
  }

  // An annotated class that delegates a suspend interface method via `by`. The only suspend call in the
  // module is the compiler-synthesized forwarding call inside the delegated `load` member.
  private fun delegationSource(): SourceFile =
    SourceFile.kotlin(
      "Sample.kt",
      """
      package sample

      import dev.one2.traceweave.annotation.TraceWeave

      interface Repo {
        suspend fun load(id: String): String
      }

      @TraceWeave
      class CachingRepo(private val delegate: Repo) : Repo by delegate
      """.trimIndent(),
    )

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
    extraRegistrars: List<CompilerPluginRegistrar> = emptyList(),
  ) = KotlinCompilation()
    .apply {
      sources = listOf(source)
      compilerPluginRegistrars = listOf(TraceWeavePluginRegistrar()) + extraRegistrars
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

private data class OffsetRecord(
  val tryStart: Int,
  val tryEnd: Int,
  val callStart: Int,
  val callEnd: Int,
)

// Registered AFTER the traceweave plugin so it observes the transformed IR: records the offsets of every
// IrTry that wraps a suspend call, letting a test assert the plugin keeps the wrapped call's source
// offsets (and thus the line-number table) intact.
@OptIn(ExperimentalCompilerApi::class)
private class CaptureRegistrar(
  private val sink: MutableList<OffsetRecord>,
) : CompilerPluginRegistrar() {
  override val pluginId: String = "traceweave-offset-capture"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    IrGenerationExtension.registerExtension(OffsetCaptureExtension(sink))
  }
}

private class OffsetCaptureExtension(
  private val sink: MutableList<OffsetRecord>,
) : IrGenerationExtension {
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    moduleFragment.acceptVoid(
      object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
          if (element is IrTry) {
            val result = element.tryResult
            if (result is IrCall && result.symbol.owner.isSuspend) {
              sink += OffsetRecord(element.startOffset, element.endOffset, result.startOffset, result.endOffset)
            }
          }
          element.acceptChildrenVoid(this)
        }
      },
    )
  }
}
