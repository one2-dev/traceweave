plugins {
  id("traceweave.conventions")
  id("traceweave.publish")
}

val kotlinVersion: String by project
val coroutinesVersion: String by project
val junitVersion: String by project
val kctforkVersion: String by project

// Hermetic local repo consumed by the gradle-plugin TestKit functional tests.
publishing {
  repositories {
    maven {
      name = "testRepo"
      url = uri(rootProject.layout.buildDirectory.dir("test-repo"))
    }
  }
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
  // The plugin emits calls into the runtime, so it reads the FQN contract straight from the runtime
  // classes instead of hardcoding strings. Non-transitive: only traceweave's own classes are needed
  // on the kotlinc plugin classpath, not slf4j.
  implementation(project(":runtime")) { isTransitive = false }

  // In-process compilation harness: register the plugin, compile a snippet, run it, assert behavior.
  testImplementation("dev.zacsweers.kctfork:core:$kctforkVersion")
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
  // The compiled snippet needs the runtime (handle/@TraceWeave/configure) and coroutines to run.
  testImplementation(project(":runtime"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

tasks.test {
  useJUnitPlatform()
}
