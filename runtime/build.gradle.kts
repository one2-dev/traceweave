plugins {
  id("traceweave.conventions")
  id("traceweave.publish")
}

// Hermetic local repo consumed by the gradle-plugin TestKit functional tests.
publishing {
  repositories {
    maven {
      name = "testRepo"
      url = uri(rootProject.layout.buildDirectory.dir("test-repo"))
    }
  }
}

val coroutinesVersion: String by project
val junitVersion: String by project
val slf4jVersion: String by project
val logbackVersion: String by project

dependencies {
  // Logging facade only: no-op unless the host app supplies a binding. Keeps the auto-injected
  // runtime light while routing traceweave's best-effort diagnostics through the standard channel.
  implementation("org.slf4j:slf4j-api:$slf4jVersion")

  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  // Test-only slf4j binding so logged events can be captured and asserted.
  testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

tasks.test {
  useJUnitPlatform()
}
