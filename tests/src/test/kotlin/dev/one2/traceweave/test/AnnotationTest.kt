package dev.one2.traceweave.test

import dev.one2.traceweave.test.classes.annotation.Clazz
import dev.one2.traceweave.test.classes.annotation.Method
import dev.one2.traceweave.test.classes.annotation.top
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class AnnotationTest {
  @Test
  fun function() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking { top() }
      }
    Assertions.assertTrue { error.hasFrames("top", "middle", "bot") }
  }

  @Test
  fun clazz() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking { Clazz().top() }
      }
    Assertions.assertTrue { error.hasFrames("top", "middle", "bot") }
  }

  @Test
  fun method() {
    val error =
      Assertions.assertThrows(Exception::class.java) {
        runBlocking { Method().top() }
      }
    Assertions.assertTrue { error.hasFrames("top", "middle", "bot") }
  }
}
