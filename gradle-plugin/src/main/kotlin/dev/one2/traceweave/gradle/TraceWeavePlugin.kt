package dev.one2.traceweave.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class TraceWeavePlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project) {
    val ext = target.extensions.create(EXTENSION_NAME, TraceWeaveExtension::class.java)
    target.pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID) {
      target.dependencies.add(IMPLEMENTATION_CONFIGURATION, "$NAMESPACE:$RUNTIME_ARTIFACT:$VERSION")
    }
    // The runtime is inert until configured. Activate it in Gradle-launched JVMs (tests and
    // `application` run) when tracing is enabled, so the zero-config Gradle path works without an
    // explicit TraceWeave.configure() call. Production JVMs opt in via configure()/properties.
    target.afterEvaluate {
      if (ext.enabled) {
        target.tasks.withType(Test::class.java).configureEach {
          it.systemProperty(ENABLED_PROPERTY, true)
        }
        target.tasks.withType(JavaExec::class.java).configureEach {
          it.systemProperty(ENABLED_PROPERTY, true)
        }
      }
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = NAMESPACE

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(NAMESPACE, COMPILER_ARTIFACT, VERSION)

  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val ext = project.extensions.getByType(TraceWeaveExtension::class.java)
    return project.provider {
      val enabled = SubpluginOption(key = OPTION_ENABLED, value = ext.enabled.toString())
      val included = ext.classes.toSubpluginOptions(OPTION_TRACED_PREFIXES)
      val excluded = ext.excludeClasses.toSubpluginOptions(OPTION_EXCLUDED_PREFIXES)
      listOf(enabled) + included + excluded
    }
  }

  private fun List<String>.toSubpluginOptions(key: String): List<SubpluginOption> =
    flatMap { it.split(LIST_SEPARATOR) }
      .map { it.trim().removeSuffix(WILDCARD_SUFFIX).removeSuffix(DOT_SUFFIX) }
      .filter { it.isNotEmpty() }
      .map { SubpluginOption(key = key, value = it) }

  private companion object {
    // Runtime activation marker: its presence makes the runtime leave its inert default. The mode
    // default (Mode.INPLACE) lives in the runtime, so the plugin carries no mode name of its own.
    const val ENABLED_PROPERTY = "traceweave.enabled"

    // Maven coordinates of the published modules (shared group plus per-module artifact ids).
    const val NAMESPACE = "dev.one2.traceweave"
    const val RUNTIME_ARTIFACT = "runtime"
    const val COMPILER_ARTIFACT = "compiler"

    const val EXTENSION_NAME = "traceWeave"
    const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
    const val IMPLEMENTATION_CONFIGURATION = "implementation"

    // Compiler-plugin option keys read by the compiler's CommandLineProcessor.
    const val OPTION_ENABLED = "enabled"
    const val OPTION_TRACED_PREFIXES = "tracedPrefixes"
    const val OPTION_EXCLUDED_PREFIXES = "excludedPrefixes"

    // Tokens for parsing the comma-separated prefix DSL (`a.b.c`, `a.b.c.*`, `x.y.Z`).
    const val LIST_SEPARATOR = ","
    const val WILDCARD_SUFFIX = "*"
    const val DOT_SUFFIX = "."

    // Read from a resource generated at build time (see gradle-plugin/build.gradle.kts) so the
    // plugin resolves the matching compiler and runtime artifacts. A resource works both from the
    // published jar and from the class-dir classpath used by Gradle TestKit functional tests.
    const val VERSION_RESOURCE = "/traceweave-version.properties"
    const val VERSION_KEY = "version"
    const val FALLBACK_VERSION = "0.0.1"

    val VERSION: String =
      TraceWeavePlugin::class.java
        .getResourceAsStream(VERSION_RESOURCE)
        ?.use {
          java.util
            .Properties()
            .apply { load(it) }
            .getProperty(VERSION_KEY)
        } ?: FALLBACK_VERSION
  }
}
