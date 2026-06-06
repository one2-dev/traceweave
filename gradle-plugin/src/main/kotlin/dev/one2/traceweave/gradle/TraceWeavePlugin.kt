package dev.one2.traceweave.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class TraceWeavePlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project) {
    target.extensions.create("traceWeave", TraceWeaveExtension::class.java)
    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      target.dependencies.add("implementation", "dev.one2.traceweave:runtime:$VERSION")
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "dev.one2.traceweave"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact("dev.one2.traceweave", "compiler", VERSION)

  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    val ext = project.extensions.getByType(TraceWeaveExtension::class.java)
    return project.provider {
      val enabled = SubpluginOption(key = "enabled", value = ext.enabled.toString())
      val included = ext.classes.toSubpluginOptions("tracedPrefixes")
      val excluded = ext.excludeClasses.toSubpluginOptions("excludedPrefixes")
      listOf(enabled) + included + excluded
    }
  }

  private fun List<String>.toSubpluginOptions(key: String): List<SubpluginOption> =
    flatMap { it.split(",") }
      .map { it.trim().removeSuffix("*").removeSuffix(".") }
      .filter { it.isNotEmpty() }
      .map { SubpluginOption(key = key, value = it) }

  private companion object {
    // Read from a resource generated at build time (see gradle-plugin/build.gradle.kts) so the
    // plugin resolves the matching compiler and runtime artifacts. A resource works both from the
    // published jar and from the class-dir classpath used by Gradle TestKit functional tests.
    val VERSION: String =
      TraceWeavePlugin::class.java.getResourceAsStream("/traceweave-version.properties")
        ?.use { java.util.Properties().apply { load(it) }.getProperty("version") } ?: "0.0.1"
  }
}
