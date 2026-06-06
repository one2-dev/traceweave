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
