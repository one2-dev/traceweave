import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("traceweave.conventions")
  application
}

val coroutinesVersion: String by project
val slf4jVersion: String by project

dependencies {
  implementation(project(":runtime"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

  // A no-op slf4j binding so the runtime's logging facade stays silent: no "no providers" notice on
  // stderr, and traceweave's soft reconfigure warnings (we switch modes between scenarios) are swallowed.
  runtimeOnly("org.slf4j:slf4j-nop:$slf4jVersion")

  // Run the local compiler plugin straight from source — no gradle-plugin, no publishing.
  "kotlinCompilerPluginClasspathMain"(project(":compiler"))
}

// What the gradle-plugin does for you in a real project; here we pass the compiler options ourselves.
// `tracedPrefixes` traces everything under the sample package; the per-function alternative is the
// `@dev.one2.traceweave.annotation.TraceWeave` annotation.
tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.freeCompilerArgs.addAll(
    "-P",
    "plugin:dev.one2.traceweave:enabled=true",
    "-P",
    "plugin:dev.one2.traceweave:tracedPrefixes=dev.one2.traceweave.samples",
  )
}

application {
  mainClass = "dev.one2.traceweave.samples.MainKt"
}
