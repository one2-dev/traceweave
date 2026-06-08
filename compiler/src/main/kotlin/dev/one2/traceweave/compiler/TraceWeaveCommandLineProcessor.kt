package dev.one2.traceweave.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

val ENABLED = CompilerConfigurationKey<Boolean>("enabled")

/** Package or class FQN prefixes whose functions are traced (in addition to `@TraceWeave`). */
val TRACED_PREFIXES = CompilerConfigurationKey<List<String>>("tracedPrefixes")

/** Package or class FQN prefixes excluded from tracing even when covered by [TRACED_PREFIXES]. */
val EXCLUDED_PREFIXES = CompilerConfigurationKey<List<String>>("excludedPrefixes")

@OptIn(ExperimentalCompilerApi::class)
class TraceWeaveCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = "dev.one2.traceweave"

  override val pluginOptions: Collection<CliOption> =
    listOf(
      CliOption(
        optionName = "enabled",
        valueDescription = "<true|false>",
        description = "Set to false to disable the plugin entirely.",
        required = false,
        allowMultipleOccurrences = false,
      ),
      CliOption(
        optionName = "tracedPrefixes",
        valueDescription = "<fqn-prefix>",
        description =
          "Package or class FQN prefix whose functions are traced (besides @TraceWeave). Repeat for multiple.",
        required = false,
        allowMultipleOccurrences = true,
      ),
      CliOption(
        optionName = "excludedPrefixes",
        valueDescription = "<fqn-prefix>",
        description =
          "Package or class FQN prefix excluded from tracing. Repeat for multiple. Exclusion wins over inclusion.",
        required = false,
        allowMultipleOccurrences = true,
      ),
    )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (option.optionName) {
      "enabled" -> configuration.put(ENABLED, value.toBoolean())
      "tracedPrefixes" ->
        configuration.put(TRACED_PREFIXES, configuration.get(TRACED_PREFIXES).orEmpty() + value)
      "excludedPrefixes" ->
        configuration.put(EXCLUDED_PREFIXES, configuration.get(EXCLUDED_PREFIXES).orEmpty() + value)
    }
  }
}
