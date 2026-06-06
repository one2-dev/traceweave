plugins {
  id("traceweave.conventions")
  id("traceweave.publish")
}

val kotlinVersion: String by project

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
}
