package dev.one2.traceweave.test

import dev.one2.traceweave.test.classes.exclude.top
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class ExcludePackageTest {
  @Test
  fun test() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking {
          top()
        }
      }

    Assertions.assertFalse { error.hasFrames("top", "middle", "bot") }
  }
}
