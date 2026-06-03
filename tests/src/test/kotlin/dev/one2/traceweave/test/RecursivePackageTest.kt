package dev.one2.traceweave.test

import dev.one2.traceweave.test.classes.recursive.top
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class RecursivePackageTest {
  @Test
  fun test() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking {
          top()
        }
      }

    Assertions.assertTrue { error.hasFrames("top", "top", "top") }
  }
}
