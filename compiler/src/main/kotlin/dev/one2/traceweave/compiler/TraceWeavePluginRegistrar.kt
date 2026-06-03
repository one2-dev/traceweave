package dev.one2.traceweave.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class TraceWeavePluginRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = "dev.one2.traceweave"

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    if (configuration.get(ENABLED) == false) return
    val prefixes = configuration.get(TRACED_PREFIXES).orEmpty()
    val excluded = configuration.get(EXCLUDED_PREFIXES).orEmpty()
    IrGenerationExtension.registerExtension(TraceWeaveIrExtension(prefixes, excluded))
  }
}
