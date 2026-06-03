package dev.one2.traceweave.test

import dev.one2.traceweave.test.classes.include.top
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class IncludePackageTest {
  @Test
  fun test() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking {
          top()
        }
      }

    Assertions.assertTrue { error.hasFrames("top", "middle", "bot") }
  }
}
