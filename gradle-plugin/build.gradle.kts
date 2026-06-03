plugins {
  id("trace-weave.conventions")
  `java-gradle-plugin`
}

val kotlinVersion: String by project

gradlePlugin {
  plugins {
    create("traceWeave") {
      id = "dev.one2.trace-weave"
      implementationClass = "dev.one2.traceweave.gradle.TraceWeavePlugin"
    }
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
