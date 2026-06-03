plugins {
  id("trace-weave.conventions")
  id("trace-weave.publish")
  `java-gradle-plugin`
  id("com.gradle.plugin-publish") version "1.3.1"
}

val kotlinVersion: String by project

gradlePlugin {
  website = "https://github.com/one2-dev/trace-weave"
  vcsUrl = "https://github.com/one2-dev/trace-weave"
  plugins {
    create("traceWeave") {
      id = "dev.one2.trace-weave"
      implementationClass = "dev.one2.traceweave.gradle.TraceWeavePlugin"
      displayName = "trace-weave"
      description = "Kotlin IR plugin that reconstructs coroutine call chains in exception stack traces"
      tags = listOf("kotlin", "coroutines", "stacktrace")
    }
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}
