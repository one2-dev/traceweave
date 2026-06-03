package dev.one2.traceweave.test

import dev.one2.traceweave.test.classes.nodelay.top
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class NoDelayPackageTest {
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
