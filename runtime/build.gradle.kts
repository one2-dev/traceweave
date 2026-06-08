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

dependencies {
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
}

tasks.test {
  useJUnitPlatform()
}
