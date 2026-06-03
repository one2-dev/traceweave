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
    target.configurations.configureEach {
      if (name.startsWith("kotlinCompilerPluginClasspath")) {
        resolutionStrategy.dependencySubstitution {
          substitute(module("dev.one2.traceweave:trace-weave-compiler"))
            .using(project(":compiler"))
        }
      }
    }
    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      target.dependencies.add(
        "implementation",
        target.dependencies.project(mapOf("path" to ":runtime")),
      )
    }
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "dev.one2.traceweave"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact("dev.one2.traceweave", "trace-weave-compiler", "0.0.1")

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
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
}
