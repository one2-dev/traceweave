plugins {
  kotlin("jvm")
  id("dev.one2.traceweave") // no version — injected by the TestKit withPluginClasspath()
}

repositories {
  // Hermetic repo published by the gradle-plugin functional test; path injected via -P.
  maven { url = uri(providers.gradleProperty("traceweaveRepo").get()) }
  mavenCentral()
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
  }
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
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.14.4")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
}

tasks.test {
  useJUnitPlatform()
}
