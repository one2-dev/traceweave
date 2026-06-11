package dev.one2.traceweave.samples

import dev.one2.traceweave.TraceWeave
import dev.one2.traceweave.mode.Mode
import dev.one2.traceweave.mode.ModeStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

private suspend fun loadUser(): String {
  delay(10.milliseconds)
  return fetchFromDb()
}

private suspend fun fetchFromDb(): String {
  delay(10.milliseconds)
  error("user not found")
}

private val customStrategy =
  object : ModeStrategy {
    override fun weave(
      error: Throwable,
      declaringClass: String,
      methodName: String,
      fileName: String,
      lineNumber: Int,
    ): Throwable =
      error.apply {
        stackTrace = emptyArray()
      }
  }

private val scenarios: List<Pair<String, () -> Unit>> =
  listOf(
    "UNCONFIGURED" to {},
    "INPLACE" to { TraceWeave.configure { mode = Mode.INPLACE } },
    "COPY" to { TraceWeave.configure { mode = Mode.COPY } },
    "COPY (no seed)" to {
      TraceWeave.configure {
        mode = Mode.COPY
        copySeedFrames = 0
      }
    },
    "CUSTOM" to { TraceWeave.configure { strategy = customStrategy } },
  )

fun main() {
  val out = StringBuilder()
  for ((label, configure) in scenarios) {
    configure()
    runBlocking {
      runCatching {
        loadUser()
      }.onFailure {
        report(out, label, it)
      }
    }
  }
  print(out)
}

private fun report(
  out: StringBuilder,
  label: String,
  error: Throwable,
) {
  out.appendLine("=".repeat(20))
  out.appendLine("Scenario: $label")
  out.appendLine("=".repeat(20))
  // Prefix every line with a zero-width space so the IDE's build view does not parse this intentional,
  // caught output as a real (uncaught) exception event and flag the run red. Leading whitespace
  // (tab/spaces) is not enough — the parser tolerates it — but U+200B is a format char, not whitespace,
  // so `^\s*<exception>` no longer matches; it stays invisible in the printed output.
  out.appendLine(error.stackTraceToString().trimEnd().prependIndent("​"))
  out.appendLine()
  out.appendLine()
  out.appendLine()
}
