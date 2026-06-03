plugins {
  id("trace-weave.conventions")
  id("dev.one2.trace-weave")
}

traceWeave {
  classes =
    listOf(
      "dev.one2.traceweave.test.classes.include",
      "dev.one2.traceweave.test.classes.nodelay",
      "dev.one2.traceweave.test.classes.recursive",
    )
  excludeClasses =
    listOf(
      "dev.one2.traceweave.test.exclude",
    )
}

val coroutinesVersion: String by project

dependencies {
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.14.4")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}
