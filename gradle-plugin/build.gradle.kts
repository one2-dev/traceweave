plugins {
  id("traceweave.conventions")
  id("traceweave.publish")
  `java-gradle-plugin`
  id("com.gradle.plugin-publish") version "1.3.1"
}

val kotlinVersion: String by project

gradlePlugin {
  website = "https://github.com/one2-dev/traceweave"
  vcsUrl = "https://github.com/one2-dev/traceweave"
  plugins {
    create("traceWeave") {
      id = "dev.one2.traceweave"
      implementationClass = "dev.one2.traceweave.gradle.TraceWeavePlugin"
      displayName = "traceweave"
      description = "Kotlin IR plugin that reconstructs coroutine call chains in exception stack traces"
      tags = listOf("kotlin", "coroutines", "stacktrace")
    }
  }
}

// Bake the project version into a resource so the plugin can resolve the matching compiler and
// runtime artifacts. A resource (unlike the jar manifest) is also present on the class-dir
// classpath that Gradle TestKit's withPluginClasspath() uses.
val writeVersion by tasks.registering(WriteProperties::class) {
  destinationFile = layout.buildDirectory.file("generated/version/traceweave-version.properties")
  property("version", project.version.toString())
}

sourceSets.main {
  resources.srcDir(writeVersion.map { it.destinationFile.get().asFile.parentFile })
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

  testImplementation(gradleTestKit())
  testImplementation(platform("org.junit:junit-bom:5.14.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
  systemProperty(
    "traceweave.sample.dir",
    layout.projectDirectory.dir("../sample").asFile.absolutePath,
  )
  systemProperty(
    "traceweave.repo.dir",
    rootProject.layout.buildDirectory.dir("test-repo").get().asFile.absolutePath,
  )
  dependsOn(
    ":runtime:publishAllPublicationsToTestRepoRepository",
    ":compiler:publishAllPublicationsToTestRepoRepository",
  )
}
