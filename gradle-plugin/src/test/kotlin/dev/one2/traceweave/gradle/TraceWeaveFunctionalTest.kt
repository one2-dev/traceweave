package dev.one2.traceweave.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Runs the standalone `e2e-tests/` consumer project through Gradle TestKit with the real
 * published plugin applied (injected via [GradleRunner.withPluginClasspath]). This exercises the actual
 * `apply()` wiring and Maven-coordinate resolution of `:compiler`/`:runtime` — which the previous
 * buildSrc-based setup never covered.
 */
class TraceWeaveFunctionalTest {
  @Test
  fun `e2e consumer tests pass with the real plugin applied`(
    @TempDir tmp: File,
  ) {
    val consumerDir = File(System.getProperty("traceweave.e2e.dir"))
    val repoDir = File(System.getProperty("traceweave.repo.dir"))
    consumerDir.copyToExcludingBuildDirs(tmp)

    val result =
      GradleRunner.create()
        .withProjectDir(tmp)
        .withArguments("test", "-PtraceweaveRepo=${repoDir.toURI()}", "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()
        .build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
  }

  private fun File.copyToExcludingBuildDirs(target: File) {
    val skip = setOf("build", ".gradle", ".kotlin")
    walkTopDown()
      .onEnter { it.name !in skip }
      .filter { it.isFile }
      .forEach { src ->
        val dest = target.resolve(src.relativeTo(this))
        dest.parentFile.mkdirs()
        src.copyTo(dest, overwrite = true)
      }
  }
}
